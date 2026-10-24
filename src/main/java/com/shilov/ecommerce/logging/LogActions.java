package com.shilov.ecommerce.logging;

public final class LogActions {

    private LogActions() {}

    public static String http(Class<?> controller, String method) {
        return "http:" + controller.getSimpleName() + "." + method;
    }

    public static String grpc(String fullMethodName) {
        int packageEnd = fullMethodName.lastIndexOf('.', fullMethodName.indexOf('/'));
        return "grpc:" + fullMethodName.substring(packageEnd + 1);
    }

    public static String kafka(String topic) {
        return "kafka:" + topic;
    }

    public static String job(Class<?> component, String method) {
        return "job:" + component.getSimpleName() + "." + method;
    }

    public static String gateway(String routeId) {
        return "gateway:" + routeId;
    }

}
