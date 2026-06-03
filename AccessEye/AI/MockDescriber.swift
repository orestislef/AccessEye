//
//  MockDescriber.swift
//  AccessEye
//
//  A stand-in for the real model so the entire app (camera → describe → speak →
//  languages → accessibility) can be built and exercised before MediaPipe and
//  the 1.3 GB model are wired in. Returns a believable description after a short
//  delay to imitate inference latency.
//
//  This is the DEFAULT describer until you add the SDK + model and flip
//  `AppConfig.useRealGemma` to true. (README §9 — M0.)
//

import UIKit

actor MockDescriber: SceneDescriber {

    func prepare() async throws {
        // Pretend to "load a model".
        try? await Task.sleep(for: .milliseconds(300))
    }

    func describe(image: UIImage, in language: Language) async throws -> String {
        // Imitate on-device inference time.
        try? await Task.sleep(for: .seconds(1.2))

        // A canned description per language so TTS + the UI can be tested with
        // believable, correctly-localized text.
        switch language {
        case .english:
            return "A room with a wooden table ahead of you. A chair is to the left, and a window with daylight is on the right. The path forward looks clear."
        case .greek:
            return "Ένα δωμάτιο με ένα ξύλινο τραπέζι μπροστά σας. Μια καρέκλα είναι στα αριστερά και ένα παράθυρο με φως στα δεξιά. Ο δρόμος μπροστά φαίνεται ελεύθερος."
        case .spanish:
            return "Una habitación con una mesa de madera delante de ti. Hay una silla a la izquierda y una ventana con luz a la derecha. El camino al frente parece despejado."
        case .french:
            return "Une pièce avec une table en bois devant vous. Une chaise est à gauche et une fenêtre avec de la lumière est à droite. Le chemin devant semble dégagé."
        case .german:
            return "Ein Raum mit einem Holztisch vor Ihnen. Links steht ein Stuhl, rechts ist ein Fenster mit Tageslicht. Der Weg nach vorne scheint frei zu sein."
        case .arabic:
            return "غرفة بها طاولة خشبية أمامك. يوجد كرسي على اليسار ونافذة بها ضوء النهار على اليمين. يبدو الطريق أمامك خاليًا."
        case .hindi:
            return "आपके सामने एक लकड़ी की मेज़ वाला कमरा है। बाईं ओर एक कुर्सी है और दाईं ओर दिन के उजाले वाली एक खिड़की है। आगे का रास्ता साफ़ दिख रहा है।"
        case .italian:
            return "Una stanza con un tavolo di legno davanti a te. C'è una sedia a sinistra e una finestra con la luce a destra. Il percorso davanti sembra libero."
        case .russian:
            return "Комната с деревянным столом перед вами. Слева стул, а справа окно с дневным светом. Путь впереди выглядит свободным."
        }
    }
}
