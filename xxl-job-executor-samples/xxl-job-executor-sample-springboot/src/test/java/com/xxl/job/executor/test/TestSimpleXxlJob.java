package com.xxl.job.executor.test;

import com.xxl.job.executor.service.jobhandler.SampleXxlJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationTargetException;

public class TestSimpleXxlJob extends NewXxlJobTest {

    @Autowired
    private SampleXxlJob sampleXxlJob;

    @Test
    public void test() throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        runJob(sampleXxlJob, "demoJobHandler");

    }
}
