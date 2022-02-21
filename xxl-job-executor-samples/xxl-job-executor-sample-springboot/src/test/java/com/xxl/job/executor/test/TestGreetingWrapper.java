package com.xxl.job.executor.test;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class TestGreetingWrapper {

    @Test
    public void test() {
        try (MockedStatic<GreetingWrapper> greetingWrapperMockedStatic = Mockito.mockStatic(GreetingWrapper.class)) {
            greetingWrapperMockedStatic.when(() -> GreetingWrapper.welcome(Mockito.anyString())).thenAnswer(Answers.RETURNS_DEFAULTS);
            GreetingWrapper.welcome( "world");
        }
    }
    @Test
    public void test2() {
        try (MockedStatic<Greeting> greetingMockedStatic = Mockito.mockStatic(Greeting.class)) {
            greetingMockedStatic.when(() -> Greeting.greet(Mockito.anyString(), Mockito.anyString())).thenAnswer(Answers.RETURNS_SMART_NULLS);
            GreetingWrapper.welcome( "world");
        }
    }
    @Test
    public void test3() {
        // without mock
        GreetingWrapper.welcome("world");
        try (MockedStatic<Greeting> greetingMockedStatic = Mockito.mockStatic(Greeting.class)) {
            ArgumentCaptor<String> valueCapture = ArgumentCaptor.forClass(String.class);
            greetingMockedStatic.when(() -> Greeting.greet(Mockito.anyString(), valueCapture.capture())).thenAnswer(invocationOnMock -> {
                System.out.println("你好 " + valueCapture.getValue());
                return null;
            });
            GreetingWrapper.welcome( "world");
        }

    }
}
