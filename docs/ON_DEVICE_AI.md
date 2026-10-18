# On-Device AI (Gemini Nano)

Sparely can write short summaries and answer questions about your money using Gemini Nano, Google's AI model that runs **entirely on your phone** through Android AICore. Your financial data never leaves the device.

Where it appears:
- **Dashboard:** "Your day at a glance" at the top of the Insights section: one or two sentences on what needs attention today, above the low balance, idle money and spending trend rows; tap it to open Insights
- **Insights:** a summary of your month with one tip, and "Ask about your finances"
- **Budgets:** a summary of how your budgets are doing
- **Vaults:** how your savings goals are going, which one needs attention and what it needs per month, with questions
- **Recurring:** what your recurring payments cost per month and per year, the biggest ones and one to review, with questions
- **Can I afford it?:** an explanation of the verdict, and questions about the purchase ("What if I wait a month?")
- **Wishlist item details:** whether now is a good time to buy it, and questions ("How long until I can afford it?")
- **Financial health:** the score explained, and questions ("How do I raise my score fastest?")

Sparely calculates every number; the model only puts them into words and never changes the verdict. The whole prompt (rules, facts, amounts and dates) is written in the app's language, English or French, so replies don't mix the two. The cards stay hidden until you turn the feature on.

**Turning it on:** Settings > On-device AI > "Use on-device AI". That screen shows whether your phone supports it, downloads the model if needed, and has a "Test with sample numbers" button.

**Supported phones:** phones where Google offers the ML Kit GenAI Prompt API (for example the Pixel 9, 9 Pro and Pixel 10 series). Sparely tries the model variants in this order and uses the first one the phone offers: stable/full, stable/fast, preview/fast. Phones with no variant just don't show the AI cards; everything else works the same.

## Phones that don't officially support it (e.g. Pixel 9a)

The Pixel 9a has a smaller Gemini Nano that Google doesn't expose to other apps yet: Sparely reports `Feature 636 is not available` (and 645 for the fast variant). You can still use it by enrolling the phone in Google's **AICore Developer Preview**, which offers the Gemini Nano 4 Fast preview model:

1. Read [Google's AICore Developer Preview guide](https://developers.google.com/ml-kit/genai/aicore-dev-preview) and check your phone is listed.
2. Join the [aicore-experimental Google group](https://groups.google.com/g/aicore-experimental/about) with the Google account on the phone.
3. Become a tester of [Android AICore](https://play.google.com/apps/testing/com.google.android.aicore) (use the link in Google's guide if this one doesn't work).
4. Update **Android AICore** from the Play Store and restart the phone.
5. Open the AICore app, go to its model picker and download **Gemini Nano 4 Fast (preview)**. Wait for the download to finish.
6. Close Sparely completely, reopen it and go to Settings > On-device AI. Turn on "Use on-device AI" and tap "Test with sample numbers".

If it worked, Logcat (tag `GeminiNano`) shows `Using Gemini Nano preview/fast`. On the Pixel 9a the preview model runs on the CPU, so answers can take several seconds.

Notes:
- Preview models are for testing: Google may change or remove them, and only enrolled phones get them.
- Status checks can report the model as ready while AICore is still downloading it; the test button is the real check.
- Sparely uses `genai-prompt` `1.0.0-beta4`, the first version that runs Gemini Nano 4: on `beta2` the preview model is found but every request fails with `INFERENCE_ERROR` 200. `beta4` has a known bug where its stable/full config isn't found on most phones ([googlesamples/mlkit#1061](https://github.com/googlesamples/mlkit/issues/1061)); Sparely then tries the next variant.

Code:
- Engine and variant selection: `ai/GeminiNanoTextModel.kt`; summarizer fallback: `ai/GeminiNanoSummarizer.kt`
- Support checks, downloads, caching and requests: `ai/LocalAiManager.kt`
- Facts sent to the model and prompts: `ai/FinanceFacts.kt`, `ai/FinancePrompt.kt`; reply cleanup: `ai/AnswerFormatter.kt`
- Cards used on screens: `ui/components/OnDeviceAi.kt`
- Unit tests (JVM): `./gradlew testDebugUnitTest --tests "com.example.sparely.ai.*"`
