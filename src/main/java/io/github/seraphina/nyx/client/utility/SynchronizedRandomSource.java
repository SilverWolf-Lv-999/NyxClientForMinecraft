package io.github.seraphina.nyx.client.utility;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public final class SynchronizedRandomSource implements RandomSource {
    private final RandomSource delegate;

    public SynchronizedRandomSource(RandomSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized RandomSource fork() {
        return new SynchronizedRandomSource(delegate.fork());
    }

    @Override
    public synchronized PositionalRandomFactory forkPositional() {
        return delegate.forkPositional();
    }

    @Override
    public synchronized void setSeed(long seed) {
        delegate.setSeed(seed);
    }

    @Override
    public synchronized int nextInt() {
        return delegate.nextInt();
    }

    @Override
    public synchronized int nextInt(int bound) {
        return delegate.nextInt(bound);
    }

    @Override
    public synchronized long nextLong() {
        return delegate.nextLong();
    }

    @Override
    public synchronized boolean nextBoolean() {
        return delegate.nextBoolean();
    }

    @Override
    public synchronized float nextFloat() {
        return delegate.nextFloat();
    }

    @Override
    public synchronized double nextDouble() {
        return delegate.nextDouble();
    }

    @Override
    public synchronized double nextGaussian() {
        return delegate.nextGaussian();
    }
}
