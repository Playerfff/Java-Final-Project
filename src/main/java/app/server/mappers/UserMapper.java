package app.server.mappers;

import app.common.models.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {
    User findByUsername(String username);
    User findById(int id);
    void insertUser(User user);

    // Admin methods
    List<User> findAll();
    void updateUser(User user);
    void deleteUser(int id);

    List<Map<String,Object>> listEmployees();
    String usernameById(int id);
}