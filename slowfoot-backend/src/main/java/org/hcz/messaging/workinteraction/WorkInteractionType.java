package org.hcz.messaging.workinteraction;


// 能影响作品统计与推荐权重的互动类型。

public enum WorkInteractionType {
    LIKE("like_count", 1.0D),
    FAVORITE("favorite_count", 2.0D),
    SHARE("share_count", 0.0D),
    HISTORY("view_count", 0.0D);

    private final String countColumn;
    private final double recommendationWeight;

    WorkInteractionType(String countColumn, double recommendationWeight) {
        this.countColumn = countColumn;
        this.recommendationWeight = recommendationWeight;
    }

    public String getCountColumn() {
        return countColumn;
    }

    public double getRecommendationWeight() {
        return recommendationWeight;
    }
}
