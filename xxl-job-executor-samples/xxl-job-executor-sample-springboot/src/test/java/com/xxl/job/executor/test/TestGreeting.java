package com.xxl.job.executor.test;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class TestGreeting {

    @Test
    public void test() {
        try (MockedStatic<Greeting> greetingMockedStatic = Mockito.mockStatic(Greeting.class)) {
            greetingMockedStatic.when(() -> Greeting.greet(Mockito.anyString(), Mockito.anyString())).thenAnswer(Answers.RETURNS_SMART_NULLS);
            Greeting.greet("hello", "world");
        }
    }
}
