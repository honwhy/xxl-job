package com.xxl.job.executor.test;

import ch.qos.logback.core.rolling.RollingFileAppender;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.server.EmbedServer;
import com.xxl.job.core.util.GsonTool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;

@SpringBootTest
public class TestXxlJob {

    @MockBean
    private XxlJobSpringExecutor xxlJobSpringExecutor;
    @Autowired
    private ApplicationContext applicationContext;
    @BeforeEach
    public void setup() {
        Mockito.doAnswer(invocationOnMock -> {

            // init JobHandler Repository (for method)
            //initJobHandlerMethodRepository(applicationContext);
            Method spyMethod = XxlJobSpringExecutor.class.getDeclaredMethod("initJobHandlerMethodRepository", ApplicationContext.class);
            spyMethod.setAccessible(true);
            spyMethod.invoke(xxlJobSpringExecutor, applicationContext);
            // refresh GlueFactory
            GlueFactory.refreshInstance(1);
            return null;
        }).when(xxlJobSpringExecutor).afterSingletonsInstantiated();

    }

    protected void runJob(String jobHandler) {

        ExecutorBiz executorBiz = new ExecutorBizImpl();
        String accessToken = "1234567890";
        ThreadPoolExecutor bizThreadPool = new ThreadPoolExecutor(
                0,
                200,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(2000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "xxl-job, EmbedServer bizThreadPool-" + r.hashCode());
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        throw new RuntimeException("xxl-job, EmbedServer bizThreadPool is EXHAUSTED!");
                    }
                });

        EmbedServer.EmbedHttpServerHandler embedHttpServerHandler = new EmbedServer.EmbedHttpServerHandler(executorBiz, accessToken, bizThreadPool);
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(embedHttpServerHandler);

        TriggerParam triggerParam = new TriggerParam();
        triggerParam.setJobId(1);
        triggerParam.setGlueType("BEAN");
        triggerParam.setExecutorHandler(jobHandler);

        String json = GsonTool.toJson(triggerParam);
        ByteBuf content = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/run", content);
        request.headers().add("XXL-JOB-ACCESS-TOKEN", accessToken);
        embeddedChannel.writeInbound(request);

        await().until(() -> bizThreadPool.getActiveCount() == 0);

        Object o = embeddedChannel.readOutbound();
        System.out.println(o);

    }
}
