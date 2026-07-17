//
//  Prompts.kt
//  AccessEye
//
//  Builds the instruction we send to Gemma alongside the camera frame.
//  Because Gemma is multilingual, the SAME prompt template produces output in
//  any supported language — we just name the target language. (README §4.)
//

package gr.orestislef.accesseye.ai

import gr.orestislef.accesseye.model.Language

object Prompts {

    /**
     * The scene-description prompt for a blind / low-vision user.
     *
     * Design notes:
     *  - We ask for a short, concrete, useful description (not flowery prose).
     *  - We ask the model to lead with the most important thing (obstacles,
     *    people, text the user might need) since the output is spoken aloud.
     *  - We instruct it to answer *only* in the target language with no
     *    preamble, so TTS doesn't read meta-commentary.
     */
    fun describeScene(language: Language): String =
        """
        You are the eyes of a blind or low-vision person. Look at this image and describe what is in front of them so they can understand and move safely.

        Guidelines:
        - Be clear, concrete and concise. Two to four short sentences.
        - Start with the most important thing (people, obstacles, hazards, or readable text such as signs, labels or screens).
        - Mention spatial layout in plain terms (left, right, ahead, close, far).
        - If there is readable text that matters, read it out.
        - Do not guess wildly; if something is unclear, say so briefly.
        - Do not describe the image as an image or mention that you are an AI.

        Respond ONLY in ${language.englishName}. Do not add any preamble, labels, or explanation — give just the description.
        ${nativeReinforcement(language)}
        """.trimIndent()

    /**
     * The same "answer only in X" instruction, written IN language X. Small
     * on-device models occasionally ignore a language request stated only in
     * English; restating it in the target language itself makes compliance
     * near-certain. (English needs no second line.)
     */
    private fun nativeReinforcement(language: Language): String = when (language) {
        Language.ENGLISH -> ""
        Language.GREEK -> "Απάντησε ΜΟΝΟ στα Ελληνικά."
        Language.SPANISH -> "Responde ÚNICAMENTE en español."
        Language.FRENCH -> "Réponds UNIQUEMENT en français."
        Language.GERMAN -> "Antworte AUSSCHLIESSLICH auf Deutsch."
        Language.ARABIC -> "أجب باللغة العربية فقط."
        Language.HINDI -> "केवल हिन्दी में उत्तर दें।"
        Language.ITALIAN -> "Rispondi SOLO in italiano."
        Language.RUSSIAN -> "Отвечай ТОЛЬКО на русском языке."
    }
}
