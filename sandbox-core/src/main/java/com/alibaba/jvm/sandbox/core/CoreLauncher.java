package com.alibaba.jvm.sandbox.core;

import com.sun.tools.attach.VirtualMachine;
import org.apache.commons.lang3.StringUtils;


import static com.alibaba.jvm.sandbox.core.util.SandboxStringUtils.getCauseMessage;

/**
 * 沙箱内核启动器
 * Created by luanjia@taobao.com on 16/10/2.
 */
public class CoreLauncher {


    public CoreLauncher(final String targetJvmPid,
                        final String agentJarPath,
                        final String token) throws Exception {

        // 加载agent
        attachAgent(targetJvmPid, agentJarPath, token);

    }

    /**
     * 内核启动程序
     * 整个命令类似于 java -jar sandbox-core.jar pid  /xxx/sandbox-agent.jar  home=xxx;token=xxx;ip=xxx;port=xxx;namespace=xxx
     *                                         [0]         [1]                     [2]
     * @param args 参数
     *             [0] : PID
     *             [1] : agent.jar's value
     *             [2] : token
     */
    public static void main(String[] args) {
        try {

            // check args
            if (args.length != 3
                    || StringUtils.isBlank(args[0])
                    || StringUtils.isBlank(args[1])
                    || StringUtils.isBlank(args[2])) {
                throw new IllegalArgumentException("illegal args");
            }

            new CoreLauncher(args[0],args[1],args[2]);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.err.println("sandbox load jvm failed : " + getCauseMessage(t));
            System.exit(-1);
        }
    }

    /**
     * 加载Agent（实质就是：利用VirtualMachine类提供的方法进行Java进程之间的通信）
     * @param targetJvmPid jvm pid
     * @param agentJarPath sandbox-agent.jar的路径
     * @param cfg 配置信息
     * @throws Exception
     */
    private void attachAgent(final String targetJvmPid,
                             final String agentJarPath,
                             final String cfg) throws Exception {

        VirtualMachine vmObj = null;
        try {
            // 获取Java进程对应的VM
            vmObj = VirtualMachine.attach(targetJvmPid);

            // 向对应的VM中加载agent
            if(vmObj!=null){
                vmObj.loadAgent(agentJarPath,cfg);
            }

        } finally {
            if (null != vmObj) {
                vmObj.detach();
            }
        }

    }

}
