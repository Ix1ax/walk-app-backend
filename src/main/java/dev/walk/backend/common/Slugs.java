package dev.walk.backend.common;

import java.util.Map;

/**
 * @author Ilya Samsonov
 * Генерация slug из названия: транслитерация кириллицы в латиницу + нормализация.
 * «Нижний Новгород» → «nizhniy-novgorod».
 */
public final class Slugs {

    private static final Map<Character, String> RU_TO_EN = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "e"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ъ', ""),
            Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
            Map.entry('я', "ya")
    );

    private Slugs() {
    }

    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : input.toLowerCase().toCharArray()) {
            if (RU_TO_EN.containsKey(c)) {
                sb.append(RU_TO_EN.get(c));
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else {
                sb.append('-');
            }
        }
        // схлопываем повторяющиеся дефисы и убираем по краям
        return sb.toString().replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
    }
}
