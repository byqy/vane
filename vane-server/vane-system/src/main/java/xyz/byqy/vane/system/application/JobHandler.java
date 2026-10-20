package xyz.byqy.vane.system.application;

/** 定时任务执行体：按 handler 名称注册，禁止在运行时加载任意代码。 */
public interface JobHandler {

    String name();

    void run(String params) throws Exception;
}
