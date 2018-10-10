package com.alibaba.jvm.sandbox.agent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import static java.lang.String.format;

/**
 * SandboxAgent启动器
 * <ul>
 *     <li>这个类的所有静态属性都必须和版本、环境无关</li>
 *     <li>这个类删除、修改方法时必须考虑多版本情况下，兼容性问题!</li>
 * </ul>
 *
 * @author luanjia@taobao.com
 */
public class AgentLauncher {

    /**
     * 配置文件路径
     * @param sandboxHome
     * @return
     */
    private static String getSandboxCfgPath(String sandboxHome) {
        return sandboxHome + File.separatorChar + "cfg";
    }

    /**
     * sandboxModule路径
     * @param sandboxHome
     * @return
     */
    private static String getSandboxModulePath(String sandboxHome) {
        return sandboxHome + File.separatorChar + "module";
    }

    /**
     * sandbox-core路径
     * @param sandboxHome
     * @return
     */
    private static String getSandboxCoreJarPath(String sandboxHome) {
        return sandboxHome + File.separatorChar + "lib" + File.separator + "sandbox-core.jar";
    }

    /**
     * sandbox spay jar路径
     * @param sandboxHome
     * @return
     */
    private static String getSandboxSpyJarPath(String sandboxHome) {
        return sandboxHome + File.separatorChar + "lib" + File.separator + "sandbox-spy.jar";
    }

    /**
     * sandbox配置文件路径
     * @param sandboxHome
     * @return
     */
    private static String getSandboxPropertiesPath(String sandboxHome) {
        return getSandboxCfgPath(sandboxHome) + File.separator + "sandbox.properties";
    }

    /**
     * provider 路径
     * @param sandboxHome
     * @return
     */
    private static String getSandboxProviderPath(String sandboxHome) {
        return sandboxHome + File.separatorChar + "provider";
    }

    /**
     * sandbox home路径
     */
    private static final String SANDBOX_HOME = File.separator + "data" + File.separator + "appdatas"+ File.separator + "sandbox";

    /**
     * sandbox home默认主目录
     */
    private static final String DEFAULT_SANDBOX_HOME
            = new File(AgentLauncher.class.getProtectionDomain().getCodeSource().getLocation().getFile())
            .getParentFile()
            .getParent();

    /**
     * sandbox user-module路径
     */
    private static final String SANDBOX_USER_MODULE_PATH
            = SANDBOX_HOME + File.separator + "user-module";

    // 启动模式: agent方式加载
    private static final String LAUNCH_MODE_AGENT = "agent";

    // 启动模式: attach方式加载
    private static final String LAUNCH_MODE_ATTACH = "attach";

    // 启动默认
    private static String LAUNCH_MODE;

    // agentmain上来的结果输出到文件/data/appdatas/sandbox/.sandbox.token
    private static final String RESULT_FILE_PATH = SANDBOX_HOME + File.separator + ".sandbox.token";

    /**
     * 全局持有ClassLoader用于隔离sandbox实现（key:nameSpace, value:自定义SandboxClassLoader）
     */
    private static volatile Map<String, SandboxClassLoader> sandboxClassLoaderMap = new ConcurrentHashMap<String, SandboxClassLoader>();

    /**
     * 内核启动配置类包路径
     */
    private static final String CLASS_OF_CORE_CONFIGURE = "com.alibaba.jvm.sandbox.core.CoreConfigure";

    // private static final String CLASS_OF_JETTY_CORE_SERVER = "com.alibaba.jvm.sandbox.core.server.jetty.JettyCoreServer";
    /**
     * 通信代理类包路径
     */
    private static final String CLASS_OF_PROXY_CORE_SERVER = "com.alibaba.jvm.sandbox.core.server.ProxyCoreServer";


    /**
     * 启动加载
     * agent方式启动（jvm参数配置中）
     * @param featureString 启动参数
     *                      [namespace,prop]
     * @param inst          inst
     */
    public static void premain(String featureString, Instrumentation inst) {
        // 启动方式：agent
        LAUNCH_MODE = LAUNCH_MODE_AGENT;
        main(toFeatureMap(featureString), inst);
    }

    /**
     * 动态加载
     * attach方式启动
     * @param featureString 启动参数
     *                      [namespace,token,ip,port,prop]
     * @param inst          inst
     */
    public static void agentmain(String featureString, Instrumentation inst) {
        // 启动方式attach
        LAUNCH_MODE = LAUNCH_MODE_ATTACH;
        final Map<String, String> featureMap = toFeatureMap(featureString);
        writeAttachResult(
                getNamespace(featureMap),
                getToken(featureMap),
                main(featureMap, inst)
        );
    }

    /**
     * 写入本次attach的结果
     * <p>
     * NAMESPACE;TOKEN;IP;PORT
     * </p>
     *
     * @param namespace 命名空间
     * @param token     操作TOKEN
     * @param local     服务器监听[IP:PORT]
     */
    private static synchronized void writeAttachResult(final String namespace,
                                                       final String token,
                                                       final InetSocketAddress local) {
        final File file = new File(RESULT_FILE_PATH);

        if (file.exists()
                && (!file.isFile()
                || !file.canWrite())) {
            throw new RuntimeException("write to result file : " + file + " failed.");
        } else {
            FileWriter fw = null;
            try {
                fw = new FileWriter(file, true);
                fw.append(
                        format("%s;%s;%s;%s\n",
                                namespace,
                                token,
                                local.getHostName(),
                                local.getPort()
                        )
                );
                fw.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (null != fw) {
                    try {
                        fw.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }


    private static synchronized ClassLoader loadOrDefineClassLoader(final String namespace,
                                                                    final String coreJar) throws Throwable {
        // 自定义classLoader
        final SandboxClassLoader classLoader;

        // 若已生成过，则直接获取返回
        if (sandboxClassLoaderMap.containsKey(namespace)
                && null != sandboxClassLoaderMap.get(namespace)) {
            classLoader = sandboxClassLoaderMap.get(namespace);
        }

        // 如果未生成过，则新生成一个
        else {
            classLoader = new SandboxClassLoader(namespace, coreJar);
            sandboxClassLoaderMap.put(namespace, classLoader);
        }

        return classLoader;
    }

    /**
     * 获取当前命名空间下的ClassLoader
     * <p>
     * 该方法将会被{@code ControlModule#shutdown}通过反射调用，
     * 请保持方法声明一致
     *
     * @param namespace 命名空间
     * @return 当前的ClassLoader
     * @since {@code sandbox-api:1.0.15}
     */
    @SuppressWarnings("unused")
    public static ClassLoader getClassLoader(final String namespace) {
        return sandboxClassLoaderMap.get(namespace);
    }

    /**
     * 清理namespace所指定的ClassLoader
     * <p>
     * 该方法将会被{@code ControlModule#shutdown}通过反射调用，
     * 请保持方法声明一致
     *
     * @param namespace 命名空间
     * @return 被清理的ClassLoader
     */
    @SuppressWarnings("unused")
    public static synchronized ClassLoader cleanClassLoader(final String namespace) {
        final SandboxClassLoader sandboxClassLoader = sandboxClassLoaderMap.remove(namespace);
        if (null != sandboxClassLoader) {
            sandboxClassLoader.closeIfPossible();
        }
        return sandboxClassLoader;
    }

    private static synchronized InetSocketAddress main(final Map<String, String> featureMap,
                                                       final Instrumentation inst) {

        // namespace
        final String namespace = getNamespace(featureMap);

        // 配置文件路径
        final String propertiesFilePath = getPropertiesFilePath(featureMap);

        // 核心配置拼装的字符串
        final String coreFeatureString = toFeatureString(featureMap);

        try {
            // spay jar的路径
            String sandboxSpyJarPath = getSandboxSpyJarPath(getSandboxHome(featureMap));

            // 将Spy注入到BootstrapClassLoader（BootClassPath的动态增补）
            inst.appendToBootstrapClassLoaderSearch(new JarFile(new File(sandboxSpyJarPath)));

            // sandbox-core jar包的路径
            String sandboxCoreJarPath = getSandboxCoreJarPath(getSandboxHome(featureMap));

            // 构造自定义的类加载器，尽量减少Sandbox对现有工程的侵蚀（将sandbox-core.jar给加载到自定义加载器）
            final ClassLoader agentLoader = loadOrDefineClassLoader(namespace, sandboxCoreJarPath);

            // 获取配置解析类（com.alibaba.jvm.sandbox.core.CoreConfigure）
            final Class<?> classOfConfigure = agentLoader.loadClass(CLASS_OF_CORE_CONFIGURE);

            // 调用CoreConfigure类的toConfigure static方法，将字符串配置解析成配置对象
            final Object objectOfCoreConfigure = classOfConfigure.getMethod("toConfigure", String.class, String.class)
                    .invoke(null, coreFeatureString, propertiesFilePath);

            // 获取内核服务代理类（com.alibaba.jvm.sandbox.core.server.ProxyCoreServer）
            final Class<?> classOfProxyServer = agentLoader.loadClass(CLASS_OF_PROXY_CORE_SERVER);

            // 获取JettyCoreServer实例（单例）
            final Object objectOfCoreServer = classOfProxyServer
                    .getMethod("getInstance")
                    .invoke(null);

            // 校验通信是否已绑定 JettyCoreServer.isBind()
            final boolean isBind = (Boolean) classOfProxyServer.getMethod("isBind").invoke(objectOfCoreServer);


            // 如果未绑定,则需要绑定一个地址
            if (!isBind) {
                try {
                    // proxyCoreServer.bind() 实例调用，非static方法
                    classOfProxyServer
                            .getMethod("bind", classOfConfigure, Instrumentation.class)
                            .invoke(objectOfCoreServer, objectOfCoreConfigure, inst);
                } catch (Throwable t) {
                    classOfProxyServer.getMethod("destroy").invoke(objectOfCoreServer);
                    throw t;
                }

            }

            // 返回服务器绑定的地址
            return (InetSocketAddress) classOfProxyServer
                    .getMethod("getLocal")
                    .invoke(objectOfCoreServer);


        } catch (Throwable cause) {
            throw new RuntimeException("sandbox attach failed.", cause);
        }

    }


    // ----------------------------------------------- 以下代码用于配置解析 -----------------------------------------------

    private static final String EMPTY_STRING = "";

    private static final String KEY_SANDBOX_HOME = "home";

    private static final String KEY_NAMESPACE = "namespace";
    private static final String DEFAULT_NAMESPACE = "default";

    private static final String KEY_SERVER_IP = "ip";
    private static final String DEFAULT_IP = "0.0.0.0";

    private static final String KEY_SERVER_PORT = "port";
    private static final String DEFAULT_PORT = "0";

    private static final String KEY_TOKEN = "token";
    private static final String DEFAULT_TOKEN = EMPTY_STRING;

    private static final String KEY_PROPERTIES_FILE_PATH = "prop";

    private static boolean isNotBlankString(final String string) {
        return null != string
                && string.length() > 0
                && !string.matches("^\\s*$");
    }

    private static boolean isBlankString(final String string) {
        return !isNotBlankString(string);
    }

    private static String getDefaultString(final String string, final String defaultString) {
        return isNotBlankString(string)
                ? string
                : defaultString;
    }

    private static Map<String, String> toFeatureMap(final String featureString) {
        final Map<String, String> featureMap = new LinkedHashMap<String, String>();

        // 不对空字符串进行解析
        if (isBlankString(featureString)) {
            return featureMap;
        }

        // KV对片段数组
        final String[] kvPairSegmentArray = featureString.split(";");
        if (kvPairSegmentArray.length <= 0) {
            return featureMap;
        }

        for (String kvPairSegmentString : kvPairSegmentArray) {
            if (isBlankString(kvPairSegmentString)) {
                continue;
            }
            final String[] kvSegmentArray = kvPairSegmentString.split("=");
            if (kvSegmentArray.length != 2
                    || isBlankString(kvSegmentArray[0])
                    || isBlankString(kvSegmentArray[1])) {
                continue;
            }
            featureMap.put(kvSegmentArray[0], kvSegmentArray[1]);
        }

        return featureMap;
    }

    private static String getDefault(final Map<String, String> map, final String key, final String defaultValue) {
        return null != map
                && !map.isEmpty()
                ? getDefaultString(map.get(key), defaultValue)
                : defaultValue;
    }

    /**
     * 获取配置中的主目录
     * @param featureMap
     * @return
     */
    private static String getSandboxHome(final Map<String, String> featureMap) {
        return getDefault(featureMap, KEY_SANDBOX_HOME, DEFAULT_SANDBOX_HOME);
    }

    /**
     * 获取配置中的命名空间
     * @param featureMap
     * @return
     */
    private static String getNamespace(final Map<String, String> featureMap) {
        return getDefault(featureMap, KEY_NAMESPACE, DEFAULT_NAMESPACE);
    }

    /**
     * 获取配置中的KEY_TOKEN文件
     * @param featureMap
     * @return
     */
    private static String getToken(final Map<String, String> featureMap) {
        return getDefault(featureMap, KEY_TOKEN, DEFAULT_TOKEN);
    }

    /**
     * 获取配置中的容器配置文件路径
     * @param featureMap
     * @return
     */
    private static String getPropertiesFilePath(final Map<String, String> featureMap) {
        return getDefault(
                featureMap,
                KEY_PROPERTIES_FILE_PATH,
                getSandboxPropertiesPath(getSandboxHome(featureMap))
                // SANDBOX_PROPERTIES_PATH
        );
    }

    // 如果featureMap中有对应的key值，则将featureMap中的[K,V]对合并到featureSB中
    private static void appendFromFeatureMap(final StringBuilder featureSB,
                                             final Map<String, String> featureMap,
                                             final String key,
                                             final String defaultValue) {
        if (featureMap.containsKey(key)) {
            featureSB.append(format("%s=%s;", key, getDefault(featureMap, key, defaultValue)));
        }
    }

    // 将featureMap中的[K,V]对转换为featureString
    private static String toFeatureString(final Map<String, String> featureMap) {
        final String sandboxHome = getSandboxHome(featureMap);
        final StringBuilder featureSB = new StringBuilder(
                format(
                        ";cfg=%s;system_module=%s;mode=%s;sandbox_home=%s;user_module=%s;provider=%s;namespace=%s;",
                        getSandboxCfgPath(sandboxHome),
                        // SANDBOX_CFG_PATH,
                        getSandboxModulePath(sandboxHome),
                        // SANDBOX_MODULE_PATH,
                        LAUNCH_MODE,
                        sandboxHome,
                        // SANDBOX_HOME,
                        SANDBOX_USER_MODULE_PATH,
                        getSandboxProviderPath(sandboxHome),
                        // SANDBOX_PROVIDER_LIB_PATH,
                        getNamespace(featureMap)
                )
        );

        // 合并IP(如有)
        appendFromFeatureMap(featureSB, featureMap, KEY_SERVER_IP, DEFAULT_IP);

        // 合并PORT(如有)
        appendFromFeatureMap(featureSB, featureMap, KEY_SERVER_PORT, DEFAULT_PORT);

        return featureSB.toString();
    }


}
