package app;

import java.io.InputStream;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSession;

public class MyBatisUtil {
    private static final SqlSessionFactory sqlSessionFactory;
    static {
        try {
            String resource = "mybatis-config.xml";
            InputStream inputStream = Resources.getResourceAsStream(resource);
            sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError("MyBatis initialization failed: " + ex.getMessage());
        }
    }

    public static SqlSession openSession() {
        return sqlSessionFactory.openSession(true);
    }
}
