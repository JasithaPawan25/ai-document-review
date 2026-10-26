package com.jasitha.docreview.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Free, offline stand-in for Claude. Finds dates and amounts with regexes so you can
 * build and test the whole workflow before spending anything on API calls.
 */
public class StubAiExtractor implements AiExtractor {

    private static final Pattern DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern AMOUNT = Pattern.compile("(?:USD|LKR|Rs\\.?|\\$)\\s?[\\d,]+(?:\\.\\d{2})?");

    @Override
    public ExtractionResult extract(String filename, String documentText) {
        List<ExtractionResult.ExtractedField> fields = new ArrayList<>();
        addMatches(fields, DATE.matcher(documentText), "date");
        addMatches(fields, AMOUNT.matcher(documentText), "amount");

        String preview = documentText.strip().replaceAll("\\s+", " ");
        if (preview.length() > 200) {
            preview = preview.substring(0, 200) + "...";
        }
        return new ExtractionResult("unknown", "[STUB] " + preview, fields);
    }

    private static void addMatches(List<ExtractionResult.ExtractedField> fields, Matcher matcher, String prefix) {
        int i = 1;
        while (matcher.find()) {
            fields.add(new ExtractionResult.ExtractedField(prefix + "_" + i++, matcher.group(), "low"));
        }
    }
}
