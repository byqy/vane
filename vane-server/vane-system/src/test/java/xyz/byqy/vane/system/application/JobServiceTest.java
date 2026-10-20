package xyz.byqy.vane.system.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.domain.SysJob;
import xyz.byqy.vane.system.domain.SysJobRepository;

class JobServiceTest {

    private final SysJobRepository jobs = mock(SysJobRepository.class);
    private final JobScheduler scheduler = mock(JobScheduler.class);
    private final JobService service = new JobService(jobs, scheduler);

    @Test
    void rejectsInvalidCron() {
        assertThatThrownBy(() -> service.create("bad", "job.bad", "cleanup-idempotency", "not-a-cron", null, "FORBID"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.VALIDATION_FAILED);
    }

    @Test
    void rejectsDuplicateJobCode() {
        when(jobs.existsByJobCodeAndDeletedAtIsNull("dup")).thenReturn(true);

        assertThatThrownBy(() -> service.create("dup", "job.dup", "cleanup-idempotency", "0 0 * * * *", null, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.REQUEST_PRECONDITION_FAILED);
    }

    @Test
    void createsJobAndSchedulesIt() {
        when(jobs.save(any(SysJob.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create("cleanup-idem", "job.cleanupIdem", "cleanup-idempotency", "0 0 * * * *", null, "FORBID");

        verify(jobs).save(any(SysJob.class));
        verify(scheduler).sync(any(SysJob.class));
    }
}
