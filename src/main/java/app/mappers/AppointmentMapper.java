package app.mappers;

import app.models.Appointment;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AppointmentMapper {
    java.util.List<Appointment> listByUser(int userId);
    java.util.List<Appointment> listByEmployee(int employeeId);
    Integer findConflict(java.util.Map<String,Object> params);
    void insertAppointment(Appointment appt);
}
