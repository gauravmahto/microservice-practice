package com.example;

/** Simple response for /run-check. */
public class RunCheckResponse {
  public String status; // "CREATED" or "REPLACED" or "ERROR"
  public String namespace;
  public String jobName;
  public String uid;
  public String message;

  public static RunCheckResponse ok(String status, String ns, String name, String uid, String msg) {
    RunCheckResponse r = new RunCheckResponse();
    r.status = status;
    r.namespace = ns;
    r.jobName = name;
    r.uid = uid;
    r.message = msg;
    return r;
  }

  public static RunCheckResponse error(String ns, String name, String msg) {
    RunCheckResponse r = new RunCheckResponse();
    r.status = "ERROR";
    r.namespace = ns;
    r.jobName = name;
    r.message = msg;
    return r;
  }
}
