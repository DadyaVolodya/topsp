package ru.infereco.demo.barista.chat;

import org.springframework.stereotype.Component;

@Component
public class RadioScript {

    private static final String[] LINES = {
            "Броня справа от старта. Не стой в дверном проёме, зомби бьют насквозь.",
            "Коридор, потом двор. Стреляй и стрейф через alt, не торчи в узкой двери.",
            "Во дворе слева секретная стена: дробовик. Возьми, пистолетом импа не мучай.",
            "Компьютерный зал: рычаг поднимает лестницу. Без рычага EXIT не откроется.",
            "Зелёный пол жжёт. Беги по краю, аптечки у стен.",
            "Ищи дверь EXIT после рычага. Tab покажет непройденные комнаты.",
            "Застрял в геометрии: жми idclip. Мало HP и стволов: idkfa. Бог: iddqd."
    };

    public String line(int elapsedSec, String lastCheat) {
        int idx = Math.min(Math.max(elapsedSec, 0) / 18, LINES.length - 1);
        String cheat = lastCheat == null || lastCheat.isBlank() || "нет".equalsIgnoreCase(lastCheat)
                ? ""
                : " Чит уже был: " + lastCheat + ".";
        return LINES[idx] + cheat;
    }
}
