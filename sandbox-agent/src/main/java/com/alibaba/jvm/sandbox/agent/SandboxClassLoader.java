package com.alibaba.jvm.sandbox.agent;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.jar.JarFile;

/**
 * 加载Sandbox用的ClassLoader
 * Created by luanjia@taobao.com on 2016/10/26.
 */
class SandboxClassLoader extends URLClassLoader {

    private final String namespace;
    private final String path;

    SandboxClassLoader(final String namespace,
                       final String sandboxCoreJarFilePath) throws MalformedURLException {
        // 父类URLClassLoader的构造器
        super(new URL[]{new URL("file:" + sandboxCoreJarFilePath)});

        // 属性赋值
        this.namespace = namespace;
        this.path = sandboxCoreJarFilePath;
    }

    /**
     * 重写loadClass方法(实现破坏双亲委派)
     * @param name
     * @param resolve
     * @return
     * @throws ClassNotFoundException
     */
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 先从已加载的类中搜寻
        final Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        try {
            // 直接从自身加载器中查找类，而不是给父加载器
            Class<?> aClass = findClass(name);

            // 连接指定类（todo 需要调研类加载过程中，为什么需要进行类连接）resolve一般是false
            if (resolve) {
                resolveClass(aClass);
            }
            return aClass;
        } catch (Exception e) {
            // 异常的时候从父加载器中查找
            return super.loadClass(name, resolve);
        }
    }

    @Override
    public String toString() {
        return String.format("SandboxClassLoader[namespace=%s;path=%s;]", namespace, path);
    }


    /**
     * 尽可能关闭ClassLoader
     * <p>
     * URLClassLoader会打开指定的URL资源，在SANDBOX中则是对应的Jar文件，如果不在shutdown的时候关闭ClassLoader，会导致下次再次加载
     * 的时候，依然会访问到上次所打开的文件（底层被缓存起来了）
     * <p>
     * 在JDK1.7版本中，URLClassLoader提供了{@code close()}方法来完成这件事；但在JDK1.6版本就要下点手段了；
     * <p>
     * 该方法将会被{@code ControlModule#shutdown}通过反射调用，
     * 请保持方法声明一致
     */
    @SuppressWarnings("unused")
    public void closeIfPossible() {

        // 如果是JDK7+的版本, URLClassLoader实现了Closeable接口，直接调用即可
        if (this instanceof Closeable) {
            try {
                final Method closeMethod = URLClassLoader.class.getMethod("close");
                closeMethod.invoke(this);
            } catch (Throwable cause) {
                // ignore...
            }
            return;
        }


        // 对于JDK6的版本，URLClassLoader要关闭起来就显得有点麻烦，这里弄了一大段代码来稍微处理下
        // 而且还不能保证一定释放干净了，至少释放JAR文件句柄是没有什么问题了
        try {
            final Object sun_misc_URLClassPath = URLClassLoader.class.getDeclaredField("ucp").get(this);
            final Object java_util_Collection = sun_misc_URLClassPath.getClass().getDeclaredField("loaders").get(sun_misc_URLClassPath);

            for (Object sun_misc_URLClassPath_JarLoader : ((Collection) java_util_Collection).toArray()) {
                try {
                    final JarFile java_util_jar_JarFile = (JarFile) sun_misc_URLClassPath_JarLoader.getClass().getDeclaredField("jar").get(sun_misc_URLClassPath_JarLoader);
                    java_util_jar_JarFile.close();
                } catch (Throwable t) {
                    // if we got this far, this is probably not a JAR loader so skip it
                }
            }

        } catch (Throwable cause) {
            // ignore...
        }

    }

}
