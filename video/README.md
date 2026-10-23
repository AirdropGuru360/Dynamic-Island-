# Промо-ролик «Динамический остров для Android»

Вертикальное видео 1080×1920 для TikTok / Reels / Shorts в стиле презентаций Apple: заставка, телефон
крупным планом и главы по функциям (зарядка, музыка, звонки, уведомления, режим звука, таймер, фонарик),
«И ещё» и финальный экран со ссылкой на GitHub. Сделано на [Remotion](https://www.remotion.dev).

Музыка и звуки синтезируются скриптом `scripts/make_audio.py` с нуля — без чужих сэмплов,
поэтому ролик можно спокойно выкладывать. Моменты звуков совпадают с кадрами в `src/IslandPromo/index.tsx`.

```bash
npm install
python3 scripts/make_audio.py              # public/audio/promo.mp3
npm run dev                                 # просмотр в Remotion Studio
npx remotion render IslandPromo out/DynamicIsland-promo.mp4 --crf=18
```

Шрифт Inter (SIL OFL) и иконки Ionicons (MIT) — те же, что в приложении.
