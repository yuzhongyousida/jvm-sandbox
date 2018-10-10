package java.com.alibaba.jvm.sandbox.spy;

import java.lang.reflect.Method;

/**
 * 间谍类，藏匿在各个ClassLoader中
 * <p>
 * 从{@code 0.0.0.v}版本之后,因为要考虑能在alipay的CloudEngine环境中使用,这个环境只能向上查找java.开头的包路径.
 * 所以这里只好把Spy的包路径前缀中增加了java.开头
 * </p>
 *
 * @author luanjia@taobao.com
 */
public class Spy {

    /*** 方法事件 ***/
    private static volatile Method ON_BEFORE_METHOD;
    private static volatile Method ON_RETURN_METHOD;
    private static volatile Method ON_THROWS_METHOD;
    private static volatile Method ON_LINE_METHOD;
    private static volatile Method ON_CALL_BEFORE_METHOD;
    private static volatile Method ON_CALL_RETURN_METHOD;
    private static volatile Method ON_CALL_THROWS_METHOD;

    private static final Class<Spy.Ret> SPY_RET_CLASS = Spy.Ret.class;

    /**
     * 初始化间谍
     *
     * @param ON_BEFORE_METHOD      ON_BEFORE 回调
     * @param ON_RETURN_METHOD      ON_RETURN 回调
     * @param ON_THROWS_METHOD      ON_THROWS 回调
     * @param ON_LINE_METHOD        ON_LINE 回调
     * @param ON_CALL_BEFORE_METHOD ON_CALL_BEFORE 回调
     * @param ON_CALL_RETURN_METHOD ON_CALL_RETURN 回调
     * @param ON_CALL_THROWS_METHOD ON_CALL_THROWS 回调
     */
    public static void init(final Method ON_BEFORE_METHOD,
                            final Method ON_RETURN_METHOD,
                            final Method ON_THROWS_METHOD,
                            final Method ON_LINE_METHOD,
                            final Method ON_CALL_BEFORE_METHOD,
                            final Method ON_CALL_RETURN_METHOD,
                            final Method ON_CALL_THROWS_METHOD) {
        Spy.ON_BEFORE_METHOD = ON_BEFORE_METHOD;
        Spy.ON_RETURN_METHOD = ON_RETURN_METHOD;
        Spy.ON_THROWS_METHOD = ON_THROWS_METHOD;
        Spy.ON_LINE_METHOD = ON_LINE_METHOD;
        Spy.ON_CALL_BEFORE_METHOD = ON_CALL_BEFORE_METHOD;
        Spy.ON_CALL_RETURN_METHOD = ON_CALL_RETURN_METHOD;
        Spy.ON_CALL_THROWS_METHOD = ON_CALL_THROWS_METHOD;
    }

    /**
     * 本地线程装载类
     */
    private static final SelfCallBarrier selfCallBarrier = new SelfCallBarrier();


    /**
     * 间谍类- on call before
     * @param lineNumber
     * @param owner
     * @param name
     * @param desc
     * @param listenerId
     * @throws Throwable
     */
    public static void spyMethodOnCallBefore(final int lineNumber,
                                             final String owner,
                                             final String name,
                                             final String desc,
                                             final int listenerId) throws Throwable {
        ON_CALL_BEFORE_METHOD.invoke(null, listenerId, lineNumber, owner, name, desc);
    }

    /**
     * 间谍类- on call return
     * @param listenerId
     * @throws Throwable
     */
    public static void spyMethodOnCallReturn(final int listenerId) throws Throwable {
        ON_CALL_RETURN_METHOD.invoke(null, listenerId);
    }

    /**
     * 间谍类- on call throws
     * @param throwException
     * @param listenerId
     * @throws Throwable
     */
    public static void spyMethodOnCallThrows(final String throwException,
                                             final int listenerId) throws Throwable {
        ON_CALL_THROWS_METHOD.invoke(null, listenerId, throwException);
    }

    /**
     * 间谍类- on line
     * @param lineNumber
     * @param listenerId
     * @throws Throwable
     */
    public static void spyMethodOnLine(final int lineNumber,
                                       final int listenerId) throws Throwable {
        ON_LINE_METHOD.invoke(null, listenerId, lineNumber);
    }

    /**
     * 间谍类- on before
     * @param argumentArray
     * @param listenerId
     * @param targetClassLoaderObjectID
     * @param javaClassName
     * @param javaMethodName
     * @param javaMethodDesc
     * @param target
     * @return
     * @throws Throwable
     */
    public static Ret spyMethodOnBefore(final Object[] argumentArray,
                                        final int listenerId,
                                        final int targetClassLoaderObjectID,
                                        final String javaClassName,
                                        final String javaMethodName,
                                        final String javaMethodDesc,
                                        final Object target) throws Throwable {
        final Thread thread = Thread.currentThread();

        // 当前线程已经装载进去，则直接返回 Ret.RET_NONE
        if (selfCallBarrier.isEnter(thread)) {
            return Ret.RET_NONE;
        }

        //
        final SelfCallBarrier.Node node = selfCallBarrier.enter(thread);
        try {
            return (Ret) ON_BEFORE_METHOD.invoke(null,
                    listenerId, targetClassLoaderObjectID, SPY_RET_CLASS, javaClassName, javaMethodName, javaMethodDesc, target, argumentArray);
        } finally {
            selfCallBarrier.exit(thread, node);
        }
    }

    /**
     * 间谍类-on return
     * @param object
     * @param listenerId
     * @return
     * @throws Throwable
     */
    public static Ret spyMethodOnReturn(final Object object,
                                        final int listenerId) throws Throwable {
        final Thread thread = Thread.currentThread();
        if (selfCallBarrier.isEnter(thread)) {
            return Ret.RET_NONE;
        }
        final SelfCallBarrier.Node node = selfCallBarrier.enter(thread);
        try {
            return (Ret) ON_RETURN_METHOD.invoke(null, listenerId, SPY_RET_CLASS, object);
        } finally {
            selfCallBarrier.exit(thread, node);
        }
    }

    /**
     * 间谍类-on throws
     * @param throwable
     * @param listenerId
     * @return
     * @throws Throwable
     */
    public static Ret spyMethodOnThrows(final Throwable throwable,
                                        final int listenerId) throws Throwable {
        final Thread thread = Thread.currentThread();
        if (selfCallBarrier.isEnter(thread)) {
            return Ret.RET_NONE;
        }
        final SelfCallBarrier.Node node = selfCallBarrier.enter(thread);
        try {
            return (Ret) ON_THROWS_METHOD.invoke(null, listenerId, SPY_RET_CLASS, throwable);
        } finally {
            selfCallBarrier.exit(thread, node);
        }
    }

    /**
     * 返回结果
     */
    public static class Ret {

        public static final int RET_STATE_NONE = 0;
        public static final int RET_STATE_RETURN = 1;
        public static final int RET_STATE_THROWS = 2;
        private static final Ret RET_NONE = new Ret(RET_STATE_NONE, null);
        /**
         * 返回状态(0:NONE;1:RETURN;2:THROWS)
         */
        public final int state;

        /**
         * 应答对象
         */
        public final Object respond;

        /**
         * 构造返回结果
         *
         * @param state   返回状态
         * @param respond 应答对象
         */
        private Ret(int state, Object respond) {
            this.state = state;
            this.respond = respond;
        }

        public static Ret newInstanceForNone() {
            return RET_NONE;
        }

        public static Ret newInstanceForReturn(Object object) {
            return new Ret(RET_STATE_RETURN, object);
        }

        public static Ret newInstanceForThrows(Throwable throwable) {
            return new Ret(RET_STATE_THROWS, throwable);
        }

    }


    /**
     * 本地线程装载类（数据结构和HashMap类似）
     */
    public static class SelfCallBarrier {
        /**
         * node数组的最大装载长度
         */
        static final int THREAD_LOCAL_ARRAY_LENGTH = 1024;

        /**
         * 装载本地thread的Node数组
         */
        final Node[] nodeArray = new Node[THREAD_LOCAL_ARRAY_LENGTH];


        SelfCallBarrier() {
            // init root node
            for (int i = 0; i < THREAD_LOCAL_ARRAY_LENGTH; i++) {
                nodeArray[i] = new Node();
            }
        }

        /**
         * 装载thread的链表节点类
         */
        public static class Node {
            private final Thread thread;
            private Node pre;
            private Node next;

            Node() {
                this(null);
            }

            Node(final Thread thread) {
                this.thread = thread;
            }
        }

        /**
         * 删除节点
         * @param node
         */
        void delete(final Node node) {
            node.pre.next = node.next;
            if (null != node.next) {
                node.next.pre = node.pre;
            }
            // help gc
            node.pre = node.next = null;
        }

        /**
         * 插入节点
         * @param top
         * @param node
         */
        void insert(final Node top, final Node node) {
            if (null != top.next) {
                top.next.pre = node;
            }
            node.next = top.next;
            node.pre = top;
            top.next = node;
        }

        /**
         * 当前node数组中是否已经装载了当前thread
         * @param thread
         * @return
         */
        boolean isEnter(Thread thread) {
            final Node top = nodeArray[thread.hashCode() % THREAD_LOCAL_ARRAY_LENGTH];
            Node node = top;
            synchronized (top) {
                while (null != node.next) {
                    node = node.next;
                    if (thread == node.thread) {
                        return true;
                    }
                }
                return false;
            }//sync
        }

        /**
         * 当前thread入node 链表
         * @param thread
         * @return
         */
        Node enter(Thread thread) {
            final Node top = nodeArray[thread.hashCode() % THREAD_LOCAL_ARRAY_LENGTH];
            final Node node = new Node(thread);
            synchronized (top) {
                insert(top, node);
            }
            return node;
        }

        /**
         * 删除node节点
         * @param thread
         * @param node
         */
        void exit(Thread thread, Node node) {
            final Node top = nodeArray[thread.hashCode() % THREAD_LOCAL_ARRAY_LENGTH];
            synchronized (top) {
                delete(node);
            }
        }

    }

}
