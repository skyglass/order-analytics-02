package net.greeta.order.customer;

import lombok.extern.slf4j.Slf4j;
import net.greeta.order.testdata.JdbcTestDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CustomerTestDataService extends JdbcTestDataService {

    @Autowired
    @Qualifier("customerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Override
    protected JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    @Override
    public void resetDatabase() {
        executeString("DELETE FROM message_log");
        executeString("DELETE FROM out_box");
        executeString("DELETE FROM customers");
    }

}
