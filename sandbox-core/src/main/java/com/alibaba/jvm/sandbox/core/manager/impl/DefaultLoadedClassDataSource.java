package com.alibaba.jvm.sandbox.core.manager.impl;

import com.alibaba.jvm.sandbox.api.filter.Filter;
import com.alibaba.jvm.sandbox.core.CoreConfigure;
import com.alibaba.jvm.sandbox.core.manager.CoreLoadedClassDataSource;
import com.alibaba.jvm.sandbox.core.util.matcher.ExtFilterMatcher;
import com.alibaba.jvm.sandbox.core.util.matcher.Matcher;
import com.alibaba.jvm.sandbox.core.util.matcher.MatchingResult;
import com.alibaba.jvm.sandbox.core.util.matcher.UnsupportedMatcher;
import com.alibaba.jvm.sandbox.core.util.matcher.structure.ClassStructureFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;
import java.util.*;

import static com.alibaba.jvm.sandbox.api.filter.ExtFilter.ExtFilterFactory.make;

/**
 * 已加载类数据源默认实现
 *
 * @author luanjia@taobao.com
 */
public class DefaultLoadedClassDataSource implements CoreLoadedClassDataSource {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Instrumentation inst;
    private final CoreConfigure cfg;

    public DefaultLoadedClassDataSource(final Instrumentation inst,
                                        final CoreConfigure cfg) {
        this.inst = inst;
        this.cfg = cfg;
    }

    @Override
    public Set<Class<?>> list() {
        final Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            classes.add(clazz);
        }
        return classes;
    }

    @Override
    public Iterator<Class<?>> iteratorForLoadedClasses() {
        return new Iterator<Class<?>>() {
            // 获取jvm 当前加载的所有类的数组
            final Class<?>[] loaded = inst.getAllLoadedClasses();
            int pos = 0;

            @Override
            public boolean hasNext() {
                return pos < loaded.length;
            }

            @Override
            public Class<?> next() {
                return loaded[pos++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    /**
     * 使用{@link Matcher}来完成类的检索
     * 本次检索将会用于Class型变，所以会主动过滤掉不支持的类和行为
     * @param matcher 类匹配
     * @return
     */
    @Override
    public List<Class<?>> findForReTransform(final Matcher matcher) {
        return find(matcher, true);
    }

    private List<Class<?>> find(final Matcher matcher,
                                final boolean isRemoveUnsupported) {
        final List<Class<?>> classes = new ArrayList<Class<?>>();
        if (null == matcher) {
            return classes;
        }

        // 取出jvm中当前加载的所有类的数组
        final Iterator<Class<?>> itForLoaded = iteratorForLoadedClasses();

        // 循环找出和matcher匹配的类，同时过滤掉jvm任务不可修改的类
        while (itForLoaded.hasNext()) {
            final Class<?> clazz = itForLoaded.next();

            // 过滤掉对于JVM认为不可修改的类
            if (isRemoveUnsupported && !inst.isModifiableClass(clazz)) {
                logger.debug("remove from findForReTransform, because class:{} is unModifiable", clazz.getName());
                continue;
            }

            try {
                if (isRemoveUnsupported) {
                    // 检查当前class是否有类和类行为有不匹配的地方
                    UnsupportedMatcher unsupportedMatcher = new UnsupportedMatcher(clazz.getClassLoader(), cfg.isEnableUnsafe());
                    unsupportedMatcher.and(matcher);
                    MatchingResult matchingResult = unsupportedMatcher.matching(ClassStructureFactory.createClassStructure(clazz));

                    // 若通过检测，则加入结果集
                    if (matchingResult.isMatched()) {
                        classes.add(clazz);
                    }
                } else {
                    if (matcher.matching(ClassStructureFactory.createClassStructure(clazz)).isMatched()) {
                        classes.add(clazz);
                    }
                }

            } catch (Throwable cause) {
                // 在这里可能会遇到非常坑爹的模块卸载错误
                // 当一个URLClassLoader被动态关闭之后，但JVM已经加载的类并不知情（因为没有GC）
                // 所以当尝试获取这个类更多详细信息的时候会引起关联类的ClassNotFoundException等未知的错误（取决于底层ClassLoader的实现）
                // 这里没有办法穷举出所有的异常情况，所以catch Throwable来完成异常容灾处理
                // 当解析类出现异常的时候，直接简单粗暴的认为根本没有这个类就好了
                logger.debug("remove from findForReTransform, because loading class:{} occur an exception", clazz.getName(), cause);
            }
        }
        return classes;
    }


    /**
     * 根据过滤器搜索出匹配的类集合
     *
     * @param filter 扩展过滤器
     * @return 匹配的类集合
     */
    @Override
    public Set<Class<?>> find(Filter filter) {
        return new LinkedHashSet<Class<?>>(find(new ExtFilterMatcher(make(filter)), false));
    }

}
