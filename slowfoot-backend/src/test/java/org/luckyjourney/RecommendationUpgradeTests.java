package org.luckyjourney;

import org.hcz.controller.CustomerController;
import org.hcz.entity.vo.WorkVO;
import org.hcz.schedul.HotRankScheduler;
import org.hcz.service.work.query.WorkQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationUpgradeTests {

    @Test
    void freshnessBonusHasExpectedBoundaries() {
        assertEquals(15.0D, HotRankScheduler.hot(0.0D, 0.0D), 0.000001D);
        assertEquals(5.0D * Math.exp(-0.011D * 2.0D), HotRankScheduler.hot(0.0D, 2.0D), 0.000001D);
        assertEquals(0.0D, HotRankScheduler.hot(0.0D, 3.0D), 0.000001D);
    }

    @Test
    void watchDurationAndScoreUseTheFourExpectedBands() {
        assertEquals(65.0D, CustomerController.durationSeconds("01:05"), 0.000001D);
        assertEquals(3723.0D, CustomerController.durationSeconds("01:02:03"), 0.000001D);
        assertEquals(0.0D, CustomerController.durationSeconds("invalid"), 0.000001D);

        assertEquals(-0.5D, CustomerController.scoreForWatchRatio(0.19D), 0.000001D);
        assertEquals(0.2D, CustomerController.scoreForWatchRatio(0.2D), 0.000001D);
        assertEquals(0.6D, CustomerController.scoreForWatchRatio(0.5D), 0.000001D);
        assertEquals(1.0D, CustomerController.scoreForWatchRatio(0.8D), 0.000001D);
    }

    @Test
    void authorDiversificationPreservesOrderAndNeverFallsBack() {
        List<WorkVO> candidates = List.of(
                work(1L, 10L), work(2L, 10L), work(3L, 10L),
                work(4L, 20L), work(5L, 30L), work(6L, 40L));

        List<WorkVO> result = WorkQueryService.diversifyByAuthor(candidates, 2, 5);

        assertEquals(List.of(1L, 2L, 4L, 5L, 6L), result.stream().map(WorkVO::getId).toList());
        assertEquals(2L, result.stream().filter(work -> work.getUserId().equals(10L)).count());
    }

    @Test
    void authorDiversificationReturnsShortResultWhenCandidatesAreInsufficient() {
        List<WorkVO> result = WorkQueryService.diversifyByAuthor(
                List.of(work(1L, 10L), work(2L, 10L), work(3L, 10L)), 2, 9);

        assertEquals(List.of(1L, 2L), result.stream().map(WorkVO::getId).toList());
    }

    private static WorkVO work(Long id, Long userId) {
        WorkVO work = new WorkVO();
        work.setId(id);
        work.setUserId(userId);
        return work;
    }
}
