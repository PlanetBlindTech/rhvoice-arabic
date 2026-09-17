# rhvoice-arabic

مشروعٌ لدعم الأصوات العربية من [RHVoice](https://github.com/RHVoice/RHVoice)، مُطَوَّرٌ بواسطة فريق [كوكب المكفوفين التقني](https://github.com/PlanetBlindTech)، ويَدْعم صوت واحد اسمه "زَيْد" حتى الآن.

---

## التفاصيل التقنية

- آلية العمل: يعتمدُ الصوتُ في جوهرِهِ على آليةٍ تُدعى التركيبُ الإحصائي [statistical parametric synthesis](https://en.wikipedia.org/wiki/Speech_synthesis#HMM-based_synthesis) عبر التقنية مفتوحة المصدر [HTS](https://hts.sp.nitech.ac.jp) بالإضافة للأدوات ذات صلة.

### مجموعة البيانات
- دُرِّبَ الصوتُ على مجموعةِ بياناتٍ اصطناعيةٍ تتكونُ من 10026 مقطعٍ صوتيٍّ مع نصوصٍ مرجعيةٍ مَشكُولة جُمِعَت مِنَ الإنترنت وأدوات الذكاء الاصطناعي.

### التمثيل الصوتي (G2P)
- كُتِبَت طبقةُ التمثيلِ الصوتي للحروفِ العربية (G2P) بأبجديَّةِ Buckwalter.

### نموذج التشكيل
- يستخدِمُ الصوتُ نموذَجَ التشكيل [Rawi](https://huggingface.co/TigreGotico/rawi-ensemble) مِن شركةِ [TigreGotico](https://huggingface.co/TigreGotico) البرتغالية.

### بيئة التدريب
- استغرقت عمليةُ التدريبِ اسبوعاً كاملاً على حاسوبٍ مُتَواضِعٍ بمعالِجٍ ثُمانِيِّ النواة، وذاكِرةٍ عشوائيةٍ تبلغُ سِتَّةَ عَشَرَ جيجابايت.

---

## طريقةُ الاستخدام

يعملُ الصوتُ حاليّاً عبرَ:

- [تطبيقٍ لِهواتِفِ Android](https://github.com/PlanetBlindTech/rhvoice-arabic/releases/download/v1.0.0-app/rhvoice-Arabic_1.0.0-release.apk)
- [إضافةٌ لِقارئِ الشاشة NVDA على Windows](https://github.com/PlanetBlindTech/rhvoice-arabic/releases/download/v1.0.0-nvda-addon/rhvoice-arabic-1.0.0.nvda-addon)

---

# شُكْرٌ وعِرْفان

نتوجَّهُ بجزيلِ الشُكرِ والامتنان إلى كُلِّ مَن ساهَمَ في تطويرِ هذا المشروع وخروجِهِ إلى النور، ونخص بالذكر:

- [mush42](https://github.com/mush42): لإتاحتهِ منطوق الذي استَوحَينا منه طريقةَ عمل الG2P.
- [riadassoum](https://github.com/riadassoum): لِإِسهاماتِهِ الكبيرة في إصلاحِ الأخطاء، وسماحِهِ لنا باستخدامِ أكوادِ المُعالجةِ المسبقةِ مِن إضافةِ [ClaritySynth](https://github.com/riadassoum/ClaritySynth).
- [Jarbas](https://github.com/JarbasAl) مِن شركةِ [TigreGotico](https://huggingface.co/TigreGotico): لاهتِمامِهِ الشديدِ باللغةِ العربيةِ ودعمِها في مجالِ الذكاءِ الاصطناعي.
- إلياس من فريق [JFF](https://jumpingfridge.gt.tc/): لِمُساعَدَتِهِ في توفيرِ نُصوصِ مجموعةِ البيانات.
- مُختَبِرِي التطبيق: لِتَجرِبَتِهِم التطبيقَ على مختلفِ الإصداراتِ مِنَ الهواتفِ وأنظمةِ التشغيل، ما ساعَدَنا في حل أكبرِ قدرٍ مِن مُشكِلاتِ الصوتِ والتوافُق.

---

## تابعنا

- [قناتنا على YouTube](https://youtube.com/@planetblindtech?si=PLR4cp13TihMeBNH)
- [قناتنا على Telegram](https://t.me/mohammad_loay222)
- [البريد الإلكتروني](mailto:planetblindtec@gmail.com)

---

حقوق النشر © 2026 فريق كوكب المكفوفين التقني. جميع الحقوق محفوظة.