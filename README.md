# NyxClient

NyxClient 是一个基于 NeoForge 的 Minecraft 客户端模组，当前面向 Minecraft `1.21.11` 开发。项目包含模块系统、事件总线、Mixin 注入、配置持久化、Click GUI、字体渲染与多语言资源。

> 当前版本仍处于早期开发阶段，部分模块已经注册但尚未实现完整行为。请只在单机、开发测试或明确允许的服务器环境中使用，并遵守目标服务器规则。

## 环境要求

- Java 21
- Minecraft 1.21.11
- NeoForge 21.11.42 或兼容的 21.x 版本
- Gradle Wrapper，仓库已包含 `gradlew` / `gradlew.bat`

## 快速开始

克隆仓库后，在项目根目录执行：

```powershell
.\gradlew.bat build
```

构建产物位于：

```text
build/libs/nyxclient-0.0.1.jar
```

开发环境运行客户端：

```powershell
.\gradlew.bat runClient
```

生成数据资源：

```powershell
.\gradlew.bat runData
```

如果你在 Linux 或 macOS 下开发，把命令中的 `.\gradlew.bat` 换成 `./gradlew`。

## 安装方式

1. 安装 Minecraft `1.21.11` 与匹配的 NeoForge。
2. 执行 `.\gradlew.bat build`。
3. 将 `build/libs/nyxclient-0.0.1.jar` 放入游戏目录的 `mods` 文件夹。
4. 启动游戏，模组 ID 为 `nyxclient`。

## 使用说明

- 默认按键：`Right Shift` 打开 Click GUI。
- 在 Click GUI 中左键点击模块条目可以启用或关闭模块。
- 鼠标滚轮可以滚动模块列表。
- 客户端配置会在首次启动后创建，并在游戏关闭时保存。

配置文件位置：

```text
<游戏目录>/nyxclient/config/modules.json
```

客户端还会创建以下目录：

```text
<游戏目录>/nyxclient/
<游戏目录>/nyxclient/config/
<游戏目录>/nyxclient/logs/
<游戏目录>/nyxclient/cages/
<游戏目录>/nyxclient/gui/
```

## 构建一个模块

NyxClient 的功能以 `Module` 为单位组织。一个模块通常需要创建模块类、声明模块信息、按需添加设置与事件监听，最后注册到 `ModuleManager`。

### 1. 创建模块类

在合适的分类目录下创建模块，例如 `module/player/ExampleModule.java`：

```java
package io.github.seraphina.nyxclient.module.player;

import io.github.seraphina.nyxclient.module.Category;
import io.github.seraphina.nyxclient.module.Module;
import io.github.seraphina.nyxclient.module.ModuleInfo;

@ModuleInfo(
    name = "nyxclient.module.example.name",
    description = "nyxclient.module.example.description",
    category = Category.PLAYER
)
public class ExampleModule extends Module {
    public static final ExampleModule INSTANCE = new ExampleModule();

    private ExampleModule() {
    }
}
```

`Category` 决定模块在 Click GUI 中出现在哪个分类下，目前可用分类为：

```text
COMBAT, MOVEMENT, PLAYER, CLIENT, OTHER, VISUAL
```

### 2. 添加模块设置

模块设置通过 `ValueBuild` 创建，并会自动加入模块的配置值列表。可用类型包括布尔、整数、小数、枚举、字符串、颜色、按键和按钮。

```java
import io.github.seraphina.nyxclient.value.ValueBuild;
import io.github.seraphina.nyxclient.value.impl.BoolValue;
import io.github.seraphina.nyxclient.value.impl.IntValue;

public class ExampleModule extends Module {
    public static final ExampleModule INSTANCE = new ExampleModule();

    public final BoolValue onlyGround = ValueBuild.boolSetting("onlyGround", true, this);
    public final IntValue delay = ValueBuild.intSetting("delay", 2, 0, 10, 1, this);

    private ExampleModule() {
    }
}
```

这些设置会保存到：

```text
<游戏目录>/nyxclient/config/modules.json
```

### 3. 监听事件

需要响应游戏行为时，在模块中添加带 `@EventTarget` 的方法。模块启用时会自动注册到事件系统，关闭时会自动注销。

```java
import io.github.seraphina.nyxclient.events.api.EventTarget;
import io.github.seraphina.nyxclient.events.impl.TickEvent;

@EventTarget
public void onTick(TickEvent.Post event) {
    if (mc.player == null) {
        return;
    }

    if (!onlyGround.getValue() || mc.player.onGround()) {
        // 在这里编写模块逻辑
    }
}
```

如果只需要在开启或关闭时执行一次逻辑，可以重写 `onEnable()` 和 `onDisable()`：

```java
@Override
public void onEnable() {
    super.onEnable();
}

@Override
public void onDisable() {
    super.onDisable();
}
```

### 4. 设置默认按键

模块可以在构造方法中设置默认按键。按键值来自 GLFW：

```java
import static org.lwjgl.glfw.GLFW.GLFW_KEY_G;

private ExampleModule() {
    this.setKey(GLFW_KEY_G);
}
```

未设置按键时，模块默认没有快捷键。

### 5. 注册模块

在 `ModuleManager.init()` 中导入并注册模块实例：

```java
import io.github.seraphina.nyxclient.module.player.ExampleModule;
```

```java
registerModule(
    FastPlace.INSTANCE,
    DelayRemover.INSTANCE,
    ExampleModule.INSTANCE
);
```

注册后，模块会出现在 Click GUI 中，并参与配置加载、保存和按键切换。

### 6. 添加语言文本

`@ModuleInfo` 中的 `name` 和 `description` 是语言键，需要在语言文件中添加对应文本。

`src/main/resources/assets/nyxclient/language/en_us.properties`：

```properties
nyxclient.module.example.name=Example
nyxclient.module.example.description=Example module
```

`src/main/resources/assets/nyxclient/language/zh_cn.properties`：

```properties
nyxclient.module.example.name=示例
nyxclient.module.example.description=示例模块
```

## 项目结构

```text
src/main/java/io/github/seraphina/nyxclient/
├── events/      # 事件 API、事件类型与事件总线
├── manager/     # 模块、配置、按键、字体、路径、旋转等管理器
├── mixins/      # Minecraft 客户端注入点
├── module/      # 功能模块与分类
├── ui/          # Click GUI 与其他界面
├── utility/     # 渲染、玩家、旋转、语言、Web 等工具类
└── value/       # 模块配置值系统

src/main/resources/
├── assets/nyxclient/fonts/       # 内置字体
├── assets/nyxclient/language/    # 多语言文件
├── assets/nyxclient/shader/      # 字体渲染 Shader
├── META-INF/accesstransformer.cfg
└── nyxclient.mixins.json
```

## 常用命令

```powershell
.\gradlew.bat build        # 构建模组
.\gradlew.bat runClient    # 启动开发客户端
.\gradlew.bat runServer    # 启动开发服务器
.\gradlew.bat runData      # 运行数据生成
.\gradlew.bat clean        # 清理构建产物
```

## 许可证

本项目使用 GNU GPL 3.0 许可证，详见 [LICENSE](LICENSE)。
