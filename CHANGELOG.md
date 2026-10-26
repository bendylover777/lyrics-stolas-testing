# 1.1.0

## Исправлено (темы)
- Кнопка пресета показывала следующий пресет вместо текущего. Теперь показывает текущую тему или `Custom`.
- Меню / остров / строка статуса брали цвет из «Glow color»: тёмное свечение делало их невидимыми. Теперь акцент осветляется до читаемого, плюс отдельная настройка UI accent.
- Список шрифтов обрезался на 12. Добавлены страницы.
- HEX-цвет из README не существовал. Добавлены HEX-поля во вкладке Theme.
- LRC: несколько таймкодов в одной строке, [offset:], словесные теги <mm:ss.xx>.

## Новое
- 22 цветовые темы, Random theme, Swap colors, UI accent / UI background.
- 13 режимов Shimmer (+Gradient, Sunset, Ocean, Candy, Matrix).
- 8 новых шрифтов (всего 20 с двумя ванильными), Bold / Italic / Underline, UPPER / lower.
- Сайты текстов: + Kuwo; переключатель для каждого сайта; запасной вариант с обычным текстом (LRCLIB, lyrics.ovh) и оценкой тайминга.
- Импорт `.lrc` из папки songs; «Words / piece» + пересборка сохранённых песен.
- Text shadow, Hide if paused, Max lines, Anim speed, Island opacity / height, Side text size.
- Горячие клавиши: текст вкл/выкл, сдвиг синхронизации, следующая тема, найти текст заново.
- Команды: fetch, search, offset, theme, font, toggle, sources, rechunk, info, reset.
