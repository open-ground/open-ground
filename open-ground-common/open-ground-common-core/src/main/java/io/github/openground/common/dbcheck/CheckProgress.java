package io.github.openground.common.dbcheck;

/**
 * 检查进度状态
 * <p>用于异步检查任务的前端进度展示。
 *
 * @author open-ground
 * @since 2026-06-18
 */
public class CheckProgress {

    /** 任务唯一标识 */
    private String taskId;

    /** 当前进度百分比 0~100 */
    private int percent;

    /** 当前步骤描述 */
    private String step;

    /** 详细消息 */
    private String message;

    /** 是否完成 */
    private boolean finished;

    /** 是否失败 */
    private boolean failed;

    /** 错误消息 */
    private String errorMessage;

    /** 检查结果（完成后填充） */
    private DbCheckResult result;

    public CheckProgress() {}

    public CheckProgress(String taskId) {
        this.taskId = taskId;
        this.percent = 0;
        this.step = "初始化";
        this.message = "准备开始检查...";
        this.finished = false;
        this.failed = false;
    }

    public void update(int percent, String step, String message) {
        this.percent = percent;
        this.step = step;
        this.message = message;
    }

    public void finish(DbCheckResult result) {
        this.percent = 100;
        this.step = "完成";
        this.message = "检查完成";
        this.finished = true;
        this.result = result;
    }

    public void fail(String errorMessage) {
        this.finished = true;
        this.failed = true;
        this.errorMessage = errorMessage;
        this.step = "失败";
        this.message = errorMessage;
    }

    // Getters
    public String getTaskId() { return taskId; }
    public int getPercent() { return percent; }
    public String getStep() { return step; }
    public String getMessage() { return message; }
    public boolean isFinished() { return finished; }
    public boolean isFailed() { return failed; }
    public String getErrorMessage() { return errorMessage; }
    public DbCheckResult getResult() { return result; }

    // Setters for Jackson
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public void setPercent(int percent) { this.percent = percent; }
    public void setStep(String step) { this.step = step; }
    public void setMessage(String message) { this.message = message; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public void setFailed(boolean failed) { this.failed = failed; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setResult(DbCheckResult result) { this.result = result; }
}
