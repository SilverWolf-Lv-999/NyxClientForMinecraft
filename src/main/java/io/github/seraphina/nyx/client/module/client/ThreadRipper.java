package io.github.seraphina.nyx.client.module.client;

import io.github.seraphina.nyx.client.module.Category;
import io.github.seraphina.nyx.client.module.Module;
import io.github.seraphina.nyx.client.module.ModuleInfo;
import io.github.seraphina.nyx.client.value.ValueBuild;
import io.github.seraphina.nyx.client.value.impl.BoolValue;
import io.github.seraphina.nyx.client.value.impl.IntValue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@ModuleInfo(name = "nyxclient.module.threadripper.name", description = "nyxclient.module.threadripper.description", category = Category.CLIENT)
public class ThreadRipper extends Module {
    public static final ThreadRipper INSTANCE = new ThreadRipper();

    private static final int DETECTED_PROCESSORS = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int MAX_THREADS = 32;
    private static final int DEFAULT_THREADS = Math.max(1, Math.min(MAX_THREADS, DETECTED_PROCESSORS - 1));
    private static final ThreadLocal<Boolean> WORKER_THREAD = ThreadLocal.withInitial(() -> false);

    public final IntValue threads = ValueBuild.intSetting("threads", DEFAULT_THREADS, 1, MAX_THREADS, 1, this);
    public final BoolValue entityTicks = ValueBuild.boolSetting("entity ticks", true, this);
    public final BoolValue blockEntityTicks = ValueBuild.boolSetting("block entity ticks", true, this);
    public final BoolValue particleTicks = ValueBuild.boolSetting("particle ticks", true, this);

    private final Object executorLock = new Object();
    private ExecutorService executor;
    private int executorThreads;

    private ThreadRipper() {
        threads.setOnChanged(ignored -> rebuildExecutor());
    }

    @Override
    public void onEnable() {
        rebuildExecutor();
    }

    @Override
    public void onDisable() {
        shutdownExecutor();
    }

    public boolean shouldParallelEntityTicks() {
        return isEnabled() && entityTicks.getValue() && configuredThreads() > 1;
    }

    public boolean shouldParallelBlockEntityTicks() {
        return isEnabled() && blockEntityTicks.getValue() && configuredThreads() > 1;
    }

    public boolean shouldParallelParticleTicks() {
        return isEnabled() && particleTicks.getValue() && configuredThreads() > 1;
    }

    public boolean isWorkerThread() {
        return WORKER_THREAD.get();
    }

    public <T> void runParallel(List<T> items, Consumer<T> action) {
        int size = items.size();
        if (size < 2 || WORKER_THREAD.get()) {
            runSerial(items, action);
            return;
        }

        int workerCount = Math.min(configuredThreads(), size);
        ExecutorService currentExecutor = getExecutor();
        if (workerCount < 2 || currentExecutor == null) {
            runSerial(items, action);
            return;
        }

        AtomicInteger cursor = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(workerCount);

        synchronized (executorLock) {
            if (currentExecutor != executor || currentExecutor.isShutdown()) {
                runSerial(items, action);
                return;
            }

            for (int worker = 0; worker < workerCount; worker++) {
                try {
                    currentExecutor.execute(() -> {
                        WORKER_THREAD.set(true);
                        try {
                            int index;
                            while (failure.get() == null && (index = cursor.getAndIncrement()) < size) {
                                action.accept(items.get(index));
                            }
                        } catch (Throwable throwable) {
                            failure.compareAndSet(null, throwable);
                        } finally {
                            WORKER_THREAD.set(false);
                            latch.countDown();
                        }
                    });
                } catch (RejectedExecutionException exception) {
                    failure.compareAndSet(null, exception);
                    latch.countDown();
                }
            }
        }

        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        Throwable throwable = failure.get();
        if (throwable != null) {
            throwUnchecked(throwable);
        }
    }

    private ExecutorService getExecutor() {
        synchronized (executorLock) {
            if (executor == null || executorThreads != configuredThreads()) {
                rebuildExecutorLocked();
            }
            return executor;
        }
    }

    private void rebuildExecutor() {
        synchronized (executorLock) {
            rebuildExecutorLocked();
        }
    }

    private void rebuildExecutorLocked() {
        shutdownExecutorLocked();
        int configuredThreads = configuredThreads();
        if (!isEnabled() || configuredThreads <= 1) {
            return;
        }

        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "Nyx ThreadRipper-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = Executors.newFixedThreadPool(configuredThreads, threadFactory);
        executorThreads = configuredThreads;
    }

    private void shutdownExecutor() {
        synchronized (executorLock) {
            shutdownExecutorLocked();
        }
    }

    private void shutdownExecutorLocked() {
        ExecutorService oldExecutor = executor;
        executor = null;
        executorThreads = 0;
        if (oldExecutor != null) {
            oldExecutor.shutdownNow();
        }
    }

    private int configuredThreads() {
        return Math.max(1, Math.min(MAX_THREADS, threads.getValue()));
    }

    private static <T> void runSerial(List<T> items, Consumer<T> action) {
        for (T item : items) {
            action.accept(item);
        }
    }

    private static void throwUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }
}
