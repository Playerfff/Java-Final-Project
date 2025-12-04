package app.common.models;

public class Appointment {
    private Integer id;
    private Integer userId;
    private Integer employeeId;
    private String date;        // YYYY-MM-DD
    private String startTime;   // HH:mm
    private String endTime;     // HH:mm
    private String status;

    public Appointment() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStartTime() {return startTime;}
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() {return endTime;}
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
