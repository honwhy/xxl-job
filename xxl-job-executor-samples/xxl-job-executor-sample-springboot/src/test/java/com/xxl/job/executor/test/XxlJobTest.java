package com.xxl.job.executor.test;

import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.impl.ExecutorBizImpl;
import com.xxl.job.core.biz.model.TriggerParam;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;

@SpringBootTest
public class XxlJobTest {

    @BeforeAll
    public static void setup() {
        //TtlAgent.premain(null, null);
    }
    protected void runJob(String jobHandler) {
        // Mock XxlJobFileAppender
        try (MockedStatic<XxlJobFileAppender> xxlJobFileAppenderMockedStatic = Mockito.mockStatic(XxlJobFileAppender.class)) {
            //xxlJobFileAppenderMockedStatic.when(() -> XxlJobFileAppender.initLogPath(Mockito.anyString())).then(Answers.RETURNS_SMART_NULLS);
            xxlJobFileAppenderMockedStatic.when(XxlJobFileAppender::getLogPath).thenReturn("/");
            ArgumentCaptor<String> valueCapture = ArgumentCaptor.forClass(String.class);
            xxlJobFileAppenderMockedStatic.when(() -> XxlJobFileAppender.appendLog(Mockito.anyString(), valueCapture.capture()))
                    .thenAnswer((Answer<Void>) invocation -> {
                        System.out.println(valueCapture.getValue());
                        return null;
                    });

            doRunJob(jobHandler);

        }
    }
    protected void doRunJob(String jobHandler) {

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
    }
}
