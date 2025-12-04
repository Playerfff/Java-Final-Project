package app.mappers;

import app.models.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    User findByUsername(String username);
    void insertUser(User user);
    List<Map<String,Object>> listEmployees();
    String usernameById(int id);
}
