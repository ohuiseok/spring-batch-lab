package com.example.batch.tasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HelloTasklet implements Tasklet {

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 1-1장은 Tasklet 실행 흐름을 보는 단계이므로, 실제 비즈니스 처리 대신 간단한 로그만 남긴다.
        log.info("Hello, Batch!");
        // FINISHED는 이 Tasklet을 반복 실행하지 않고 Step을 끝내도 된다는 신호다.
        return RepeatStatus.FINISHED;
    }
}
