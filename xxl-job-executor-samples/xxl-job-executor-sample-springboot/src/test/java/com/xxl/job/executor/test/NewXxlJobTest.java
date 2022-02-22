package com.xxl.job.executor.test;

import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.server.EmbedServer;
import com.xxl.job.core.thread.JobThread;
import com.xxl.job.core.thread.TriggerCallbackThread;
import com.xxl.job.core.util.GsonTool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadPoolExecutor;

import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class NewXxlJobTest {

    @MockBean
    private XxlJobSpringExecutor xxlJobSpringExecutor;

    public void runJob(Object bean, String jobHandlerName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        // make IJobHandler
        Map<Method, XxlJob> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
        try {
            annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                    new MethodIntrospector.MetadataLookup<XxlJob>() {
                        @Override
                        public XxlJob inspect(Method method) {
                            return AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
                        }
                    });
        } catch (Throwable ignored) {

        }
        Assertions.assertNotNull(annotatedMethods);
        Assertions.assertFalse(annotatedMethods.isEmpty());
        for (Map.Entry<Method, XxlJob> entry : annotatedMethods.entrySet()) {
            String methodName = entry.getKey().getName();
            XxlJob xxlJob = entry.getValue();
            if (methodName.equals(jobHandlerName)) {
                // reflect
                Method registerMethod = XxlJobExecutor.class.getDeclaredMethod("registJobHandler", XxlJob.class, Object.class, Method.class);
                registerMethod.setAccessible(true);
                registerMethod.invoke(new StubXxlJobExecutor(), xxlJob, bean, entry.getKey());
                break;
            }
        }
        IJobHandler jobHandler = XxlJobExecutor.loadJobHandler(jobHandlerName);
        Assertions.assertNotNull(jobHandler);
        // reflect again
        Field jobThreadRepositoryField = XxlJobExecutor.class.getDeclaredField("jobThreadRepository");
        jobThreadRepositoryField.setAccessible(true);
        ConcurrentMap<Integer, JobThread> jobThreadRepository = ((ConcurrentMap<Integer, JobThread> ) jobThreadRepositoryField.get(null));
        int jobId = 1;
        JobThread jobThread = new JobThread(jobId, jobHandler);
        jobThreadRepository.put(jobId, jobThread);

        // JobThread Run in the same thread ?
        runThreadByNetty(jobHandlerName, jobId, jobThread);
    }

    private void runThreadByNetty(String jobHandlerName, int jobId, JobThread jobThread) throws NoSuchFieldException, IllegalAccessException {

        ExecutorBiz executorBiz = new ExecutorBizImpl();
        String accessToken = "1234567890";
        // 第一层异步变成同步
        ThreadPoolExecutor bizThreadPool = Mockito.mock(ThreadPoolExecutor.class);
        ArgumentCaptor<Runnable> runnableArgumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.doAnswer((Answer<Void>) invocation -> {
            runnableArgumentCaptor.getValue().run();
            return null;
        }).when(bizThreadPool).execute(runnableArgumentCaptor.capture());

        EmbedServer.EmbedHttpServerHandler embedHttpServerHandler = new EmbedServer.EmbedHttpServerHandler(executorBiz, accessToken, bizThreadPool);
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(embedHttpServerHandler);

        TriggerParam triggerParam = new TriggerParam();
        triggerParam.setJobId(jobId);
        triggerParam.setGlueType("BEAN");
        triggerParam.setExecutorHandler(jobHandlerName);

        String json = GsonTool.toJson(triggerParam);
        ByteBuf content = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/run", content);
        request.headers().add("XXL-JOB-ACCESS-TOKEN", accessToken);
        embeddedChannel.writeInbound(request);

        // 第一层异步NettyHandler改同步后接收到报文
        // 执行XxlJobExecutor.loadJobHandler
        // 执行XxlJobExecutor.loadJobThread等相关操作后
        // 会向JobThread 投递TriggerParam
        // 此时刚好是JobThread正确的启动时机
        // 是否还需要对JobThread使用到的日志记录等静态方法做Mock
        // 此时JobThread不能以异步方式执行
        // 是否要自己包装一层异步让JobThread与静态模块整体异步启动
        // 在之后监测JobThread完成情况通知JobThread结束执行
        Thread testThread = new Thread(() ->{
            try (MockedStatic<XxlJobHelper> xxlJobHelperMockedStatic = Mockito.mockStatic(XxlJobHelper.class);
                 //MockedStatic<XxlJobFileAppender> xxlJobFileAppenderMockedStatic = Mockito.mockStatic(XxlJobFileAppender.class);
                 MockedStatic<TriggerCallbackThread> triggerCallbackThreadMockedStatic = Mockito.mockStatic(TriggerCallbackThread.class)
            ) {

                ArgumentCaptor<Object[]> valueCapture = ArgumentCaptor.forClass(Object[].class);
                xxlJobHelperMockedStatic.when(() -> XxlJobHelper.log(Mockito.anyString(), valueCapture.capture()))
                        .thenAnswer((Answer<Void>) invocation -> {
                            System.out.println(valueCapture.getValue());
                            return null;
                        });
                ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
                xxlJobHelperMockedStatic.when(() -> XxlJobHelper.log(stringArgumentCaptor.capture()))
                        .thenAnswer((Answer<Void>) invocation -> {
                            System.out.println(stringArgumentCaptor.getValue());
                            return null;
                        });


                jobThread.run();
            }

        });
        testThread.start();
        // 表示 JobThread执行完毕
        await().forever().until(() -> !jobThread.isRunningOrHasQueue());
        // reflect
        Field toStopField = JobThread.class.getDeclaredField("toStop");
        toStopField.setAccessible(true);
        toStopField.set(jobThread, true);

        await().forever().until(() -> !testThread.isAlive());

    }

    private static class StubXxlJobExecutor extends XxlJobExecutor {

    }
}

