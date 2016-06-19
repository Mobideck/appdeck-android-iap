package com.mobideck.appdeck.plugin;

import org.json.JSONArray;
import org.json.JSONObject;

public abstract class ApiCall {
    public String command;
    public String eventID;
    public String inputJSON;
    public JSONObject inputObject;
    public JSONObject paramObject;

    public String resultJSON;
    public Boolean success;
    public Boolean callBackSend;

    abstract public void sendCallBackWithError(String error);

    abstract public void sendCallbackWithResult(String type, String result);

    abstract public void sendCallbackWithResult(String type, JSONObject result);

    abstract public void sendCallbackWithResult(String type, JSONArray results);

    abstract public void setResultJSON(String json);

    abstract public void setResult(Object res);

    abstract public void postponeResult();

    abstract public void sendPostponeResult(Boolean r);

    abstract public void sendResult(Boolean r);
}
