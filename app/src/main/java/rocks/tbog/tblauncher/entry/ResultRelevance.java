package rocks.tbog.tblauncher.entry;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import rocks.tbog.tblauncher.normalizer.StringNormalizer;
import rocks.tbog.tblauncher.utils.FuzzyScore;

public class ResultRelevance implements Comparable<ResultRelevance> {

    private final ArrayList<ResultInfo> infoList = new ArrayList<>();
    private int scoreBoost = 0;

    public int getRelevance() {
        int score = scoreBoost;
        for (ResultInfo info : infoList)
            score += info.relevance.score;
        return score;
    }

    public void addMatchInfo(@NonNull StringNormalizer.Result matchedText, @Nullable FuzzyScore.MatchInfo matchInfo) {
        final ResultInfo resultInfo;
        if (matchInfo == null)
            resultInfo = new ResultInfo(matchedText, new FuzzyScore.MatchInfo());
        else
            resultInfo = new ResultInfo(matchedText, new FuzzyScore.MatchInfo(matchInfo));
        infoList.add(resultInfo);
    }

    public void setMatchInfo(@NonNull StringNormalizer.Result normalizedName, @Nullable FuzzyScore.MatchInfo matchInfo) {
        resetRelevance();
        addMatchInfo(normalizedName, matchInfo);
    }

    public void boostRelevance(int boost) {
        scoreBoost += boost;
    }

    public void resetRelevance() {
        infoList.clear();
        scoreBoost = 0;
    }

    public Collection<ResultInfo> getInfoList() {
        return infoList;
    }

    @Override
    public int compareTo(ResultRelevance o) {
        int difference = getRelevance() - o.getRelevance();
        if (difference == 0) {
            difference = scoreBoost - o.scoreBoost;
            if (difference == 0) {
                StringNormalizer.Result rSource = infoList.size() > 0 ? infoList.get(0).relevanceSource : null;
                StringNormalizer.Result o_rSource = o.infoList.size() > 0 ? o.infoList.get(0).relevanceSource : null;
                if (rSource != null && o_rSource != null)
                    return rSource.compareTo(o_rSource);
            }
        }
        return difference;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResultRelevance))
            return false;
        return compareTo((ResultRelevance) o) == 0;
    }

    public static class ResultInfo {

        // How relevant is this record? The higher, the most probable it will be displayed
        @NonNull
        public final FuzzyScore.MatchInfo relevance;
        // Pointer to the normalizedName that the above relevance was calculated, used for highlighting
        @NonNull
        public final StringNormalizer.Result relevanceSource;

        private ResultInfo(@NonNull StringNormalizer.Result relevanceSource, @NonNull FuzzyScore.MatchInfo relevance) {
            this.relevance = relevance;
            this.relevanceSource = relevanceSource;
        }
    }
}
