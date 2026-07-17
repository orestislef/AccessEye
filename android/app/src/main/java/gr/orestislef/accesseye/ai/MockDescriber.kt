//
//  MockDescriber.kt
//  AccessEye
//
//  A stand-in for the real model so the entire app (camera → describe → speak →
//  languages → accessibility) can be built and exercised before MediaPipe and
//  the model are wired in. Returns a believable description after a short
//  delay to imitate inference latency.
//
//  This is the DEFAULT describer until you add the SDK + model and flip
//  `AppConfig.useRealGemma` to true. (README §9 — M0.)
//

package gr.orestislef.accesseye.ai

import android.graphics.Bitmap
import gr.orestislef.accesseye.model.Language
import kotlinx.coroutines.delay

class MockDescriber : SceneDescriber {

    override suspend fun prepare() {
        // Pretend to "load a model".
        delay(300)
    }

    override suspend fun describe(image: Bitmap, language: Language): String {
        // Imitate on-device inference time.
        delay(1200)

        // A canned description per language so TTS + the UI can be tested with
        // believable, correctly-localized text.
        return when (language) {
            Language.ENGLISH ->
                "A room with a wooden table ahead of you. A chair is to the left, and a window with daylight is on the right. The path forward looks clear."
            Language.GREEK ->
                "Ένα δωμάτιο με ένα ξύλινο τραπέζι μπροστά σας. Μια καρέκλα είναι στα αριστερά και ένα παράθυρο με φως στα δεξιά. Ο δρόμος μπροστά φαίνεται ελεύθερος."
            Language.SPANISH ->
                "Una habitación con una mesa de madera delante de ti. Hay una silla a la izquierda y una ventana con luz a la derecha. El camino al frente parece despejado."
            Language.FRENCH ->
                "Une pièce avec une table en bois devant vous. Une chaise est à gauche et une fenêtre avec de la lumière est à droite. Le chemin devant semble dégagé."
            Language.GERMAN ->
                "Ein Raum mit einem Holztisch vor Ihnen. Links steht ein Stuhl, rechts ist ein Fenster mit Tageslicht. Der Weg nach vorne scheint frei zu sein."
            Language.ARABIC ->
                "غرفة بها طاولة خشبية أمامك. يوجد كرسي على اليسار ونافذة بها ضوء النهار على اليمين. يبدو الطريق أمامك خاليًا."
            Language.HINDI ->
                "आपके सामने एक लकड़ी की मेज़ वाला कमरा है। बाईं ओर एक कुर्सी है और दाईं ओर दिन के उजाले वाली एक खिड़की है। आगे का रास्ता साफ़ दिख रहा है।"
            Language.ITALIAN ->
                "Una stanza con un tavolo di legno davanti a te. C'è una sedia a sinistra e una finestra con la luce a destra. Il percorso davanti sembra libero."
            Language.RUSSIAN ->
                "Комната с деревянным столом перед вами. Слева стул, а справа окно с дневным светом. Путь впереди выглядит свободным."
        }
    }
}
