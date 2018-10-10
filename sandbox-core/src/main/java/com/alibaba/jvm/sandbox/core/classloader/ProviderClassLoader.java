package com.alibaba.jvm.sandbox.core.classloader;

import com.alibaba.jvm.sandbox.api.annotation.Stealth;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * 服务提供库ClassLoader
 * 是可路由的ClassLoader实现类，父ClassLoader是sandboxClassLoader
 * 其实就是对
 * com.alibaba.jvm.sandbox.api..*
 * com.alibaba.jvm.sandbox.provider..*
 * javax.annotation.Resource.*$
 * 符合这些正则包路径的类的缓存ClassLoader而已
 *
 * @author luanjia@taobao.com
 */
@Stealth
public class ProviderClassLoader extends RoutingURLClassLoader {

    public ProviderClassLoader(final File providerJarFile,
                               final ClassLoader sandboxClassLoader) throws IOException {
        super(
                new URL[]{new URL("file:" + providerJarFile.getPath())},
                new Routing(
                        sandboxClassLoader,
                        "^com\\.alibaba\\.jvm\\.sandbox\\.api\\..*",
                        "^com\\.alibaba\\.jvm\\.sandbox\\.provider\\..*",
                        "^javax\\.annotation\\.Resource.*$"
                )
        );
    }
}
