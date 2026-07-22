package com.foresight.fcms.dashboard;

import com.foresight.fcms.auth.UserAccountRepository;
import com.foresight.fcms.client.ClientService;
import com.foresight.fcms.finance.FinanceService;
import com.foresight.fcms.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final UserAccountRepository users;
    private final ClientService clients;
    private final TaskService tasks;
    private final FinanceService finance;

    public DashboardService(
        UserAccountRepository users,
        ClientService clients,
        TaskService tasks,
        FinanceService finance
    ) {
        this.users = users;
        this.clients = clients;
        this.tasks = tasks;
        this.finance = finance;
    }

    @Transactional(readOnly = true)
    public DashboardMetrics metrics() {
        return new DashboardMetrics(
            users.count(),
            clients.countSaccos(),
            tasks.countTasks(),
            finance.countExpenses()
        );
    }
}
