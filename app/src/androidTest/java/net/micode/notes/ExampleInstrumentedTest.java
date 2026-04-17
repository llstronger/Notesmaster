package net.micode.notes; // 声明当前类所在的包名为 net.micode.notes

import android.content.Context; // 导入 Android 系统的 Context（上下文）类

import androidx.test.platform.app.InstrumentationRegistry; // 导入用于获取仪器测试环境信息的 InstrumentationRegistry 类
import androidx.test.ext.junit.runners.AndroidJUnit4; // 导入 AndroidJUnit4 测试运行器类

import org.junit.Test; // 导入 JUnit 框架的 @Test 注解，用于标记一个方法为测试用例
import org.junit.runner.RunWith; // 导入 JUnit 框架的 @RunWith 注解，用于指定执行测试的运行器

import static org.junit.Assert.*; // 静态导入 JUnit 的所有断言方法（例如 assertEquals，可以直接调用而不需要类名前缀）

/**
 * 仪器化测试（Instrumented test），将会在 Android 设备（真机或模拟器）上执行。
 *
 * @see <a href="http://d.android.com/tools/testing">测试文档</a>
 */
@RunWith(AndroidJUnit4.class) // 指定使用 AndroidJUnit4 来运行下面这个测试类，这是 Android UI 和环境测试的标准运行器
public class ExampleInstrumentedTest { // 定义一个名为 ExampleInstrumentedTest 的公开测试类

    @Test // 使用 @Test 注解，标记紧接着的 useAppContext() 方法是一个独立的测试用例
    public void useAppContext() { // 定义测试方法：获取并验证应用上下文
        // 获取正在被测试的应用的 Context（上下文）
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext(); // 通过 InstrumentationRegistry 获取目标测试应用的环境上下文对象

        assertEquals("net.micode.notes", appContext.getPackageName()); // 断言（验证）获取到的应用包名是否与预期的 "net.micode.notes" 完全相等。如果不等，则测试不通过
    }
}