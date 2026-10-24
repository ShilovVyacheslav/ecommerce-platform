package com.shilov.ecommerce.logging;

public final class RequestLogAttributes {

    public static final String ACTION = RequestLogAttributes.class.getName() + ".action";
    public static final String USER_ID = RequestLogAttributes.class.getName() + ".userId";
    public static final String ERROR_CODE = RequestLogAttributes.class.getName() + ".errorCode";

    private RequestLogAttributes() {}

}
