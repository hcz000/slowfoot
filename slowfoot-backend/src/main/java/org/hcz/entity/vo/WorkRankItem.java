package org.hcz.entity.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;



@Data
@NoArgsConstructor
@ToString
public class WorkRankItem implements  Serializable {

    private static final long serialVersionUID = 1L;

    String hotFormat;

    Double hot;

    Long workId;

    String title;

    public WorkRankItem(Double hot,Long workId,String title){
        this.hot = hot;
        this.workId = workId;
        this.title = title;
    }


    public void hotFormat(){
        BigDecimal bigDecimal = new BigDecimal(this.hot);
        BigDecimal decimal = bigDecimal.divide(new BigDecimal("10000"));
        DecimalFormat formater = new DecimalFormat("0.0");
        formater.setRoundingMode(RoundingMode.HALF_UP);    // 5000008.89
        String formatNum = formater.format(decimal);
        this.setHotFormat( formatNum+"万");
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkRankItem rankItem = (WorkRankItem) o;
        return Objects.equals(workId, rankItem.workId) &&
                Objects.equals(title, rankItem.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workId, title);
    }
}
