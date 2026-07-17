//
//  LocalizedUI.swift
//  AccessEye
//
//  The app's spoken + on-screen wording follows the user's CHOSEN output
//  language (not the device locale), because that's the language the blind user
//  asked to hear. Descriptions themselves come from Gemma in that language; this
//  table covers the app's own status/labels so the whole experience is
//  consistent. (README §6.4, §7.)
//

import Foundation

struct UIText {
    let gettingReady: String
    let ready: String
    let describing: String
    let tapToDescribe: String
    let repeatLast: String
    let history: String
    let settings: String
    let language: String
    let speechSpeed: String
    let noHistory: String
    let clearHistory: String
    let cameraNeeded: String
    let notReady: String
    let done: String
    // Newer labels — English defaults so every language keeps building; localize
    // per-case over time.
    var stop: String = "Stop"
    var clear: String = "Clear"
    var tapToHear: String = "Tap to hear again"
}

enum LocalizedUI {
    static func text(for language: Language) -> UIText {
        switch language {
        case .english:
            return UIText(
                gettingReady: "Getting things ready…",
                ready: "Ready. Tap anywhere to describe what's in front of you.",
                describing: "Looking…",
                tapToDescribe: "Tap to describe",
                repeatLast: "Repeat",
                history: "History",
                settings: "Settings",
                language: "Language",
                speechSpeed: "Speech speed",
                noHistory: "No descriptions yet.",
                clearHistory: "Clear history",
                cameraNeeded: "Camera access is needed. Please turn it on in Settings.",
                notReady: "Couldn't get the AI ready.",
                done: "Done")
        case .greek:
            return UIText(
                gettingReady: "Ετοιμάζομαι…",
                ready: "Έτοιμο. Αγγίξτε οπουδήποτε για να περιγράψω τι είναι μπροστά σας.",
                describing: "Κοιτάζω…",
                tapToDescribe: "Αγγίξτε για περιγραφή",
                repeatLast: "Επανάληψη",
                history: "Ιστορικό",
                settings: "Ρυθμίσεις",
                language: "Γλώσσα",
                speechSpeed: "Ταχύτητα ομιλίας",
                noHistory: "Δεν υπάρχουν περιγραφές ακόμη.",
                clearHistory: "Εκκαθάριση ιστορικού",
                cameraNeeded: "Απαιτείται πρόσβαση στην κάμερα. Ενεργοποιήστε την στις Ρυθμίσεις.",
                notReady: "Δεν ήταν δυνατή η προετοιμασία της τεχνητής νοημοσύνης.",
                done: "Τέλος",
                stop: "Διακοπή",
                clear: "Καθαρισμός",
                tapToHear: "Αγγίξτε για ακρόαση ξανά")
        case .spanish:
            return UIText(
                gettingReady: "Preparando todo…",
                ready: "Listo. Toca en cualquier lugar para describir lo que tienes delante.",
                describing: "Mirando…",
                tapToDescribe: "Toca para describir",
                repeatLast: "Repetir",
                history: "Historial",
                settings: "Ajustes",
                language: "Idioma",
                speechSpeed: "Velocidad de voz",
                noHistory: "Aún no hay descripciones.",
                clearHistory: "Borrar historial",
                cameraNeeded: "Se necesita acceso a la cámara. Actívalo en Ajustes.",
                notReady: "No se pudo preparar la IA.",
                done: "Hecho",
                stop: "Detener",
                clear: "Borrar",
                tapToHear: "Toca para escuchar de nuevo")
        case .french:
            return UIText(
                gettingReady: "Préparation en cours…",
                ready: "Prêt. Touchez n'importe où pour décrire ce qui est devant vous.",
                describing: "Observation…",
                tapToDescribe: "Touchez pour décrire",
                repeatLast: "Répéter",
                history: "Historique",
                settings: "Réglages",
                language: "Langue",
                speechSpeed: "Vitesse de parole",
                noHistory: "Aucune description pour l'instant.",
                clearHistory: "Effacer l'historique",
                cameraNeeded: "L'accès à la caméra est nécessaire. Activez-le dans Réglages.",
                notReady: "Impossible de préparer l'IA.",
                done: "Terminé",
                stop: "Arrêter",
                clear: "Effacer",
                tapToHear: "Touchez pour réécouter")
        case .german:
            return UIText(
                gettingReady: "Wird vorbereitet…",
                ready: "Bereit. Tippen Sie irgendwo, um zu beschreiben, was vor Ihnen ist.",
                describing: "Schaue…",
                tapToDescribe: "Zum Beschreiben tippen",
                repeatLast: "Wiederholen",
                history: "Verlauf",
                settings: "Einstellungen",
                language: "Sprache",
                speechSpeed: "Sprechgeschwindigkeit",
                noHistory: "Noch keine Beschreibungen.",
                clearHistory: "Verlauf löschen",
                cameraNeeded: "Kamerazugriff ist erforderlich. Bitte in den Einstellungen aktivieren.",
                notReady: "Die KI konnte nicht vorbereitet werden.",
                done: "Fertig",
                stop: "Stopp",
                clear: "Löschen",
                tapToHear: "Zum erneuten Anhören tippen")
        case .arabic:
            return UIText(
                gettingReady: "جارٍ التحضير…",
                ready: "جاهز. المس أي مكان لوصف ما أمامك.",
                describing: "جارٍ النظر…",
                tapToDescribe: "المس للوصف",
                repeatLast: "إعادة",
                history: "السجل",
                settings: "الإعدادات",
                language: "اللغة",
                speechSpeed: "سرعة الكلام",
                noHistory: "لا توجد أوصاف بعد.",
                clearHistory: "مسح السجل",
                cameraNeeded: "يلزم الوصول إلى الكاميرا. يرجى تفعيله من الإعدادات.",
                notReady: "تعذّر تجهيز الذكاء الاصطناعي.",
                done: "تم",
                stop: "إيقاف",
                clear: "مسح",
                tapToHear: "المس للاستماع مرة أخرى")
        case .hindi:
            return UIText(
                gettingReady: "तैयारी हो रही है…",
                ready: "तैयार। आपके सामने जो है उसका वर्णन करने के लिए कहीं भी टैप करें।",
                describing: "देख रहा हूँ…",
                tapToDescribe: "वर्णन के लिए टैप करें",
                repeatLast: "दोहराएँ",
                history: "इतिहास",
                settings: "सेटिंग्ज़",
                language: "भाषा",
                speechSpeed: "बोलने की गति",
                noHistory: "अभी तक कोई वर्णन नहीं।",
                clearHistory: "इतिहास साफ़ करें",
                cameraNeeded: "कैमरा एक्सेस आवश्यक है। कृपया इसे सेटिंग्ज़ में चालू करें।",
                notReady: "एआई तैयार नहीं हो सका।",
                done: "हो गया",
                stop: "रोकें",
                clear: "हटाएँ",
                tapToHear: "फिर से सुनने के लिए टैप करें")
        case .italian:
            return UIText(
                gettingReady: "Preparazione in corso…",
                ready: "Pronto. Tocca ovunque per descrivere ciò che hai davanti.",
                describing: "Sto guardando…",
                tapToDescribe: "Tocca per descrivere",
                repeatLast: "Ripeti",
                history: "Cronologia",
                settings: "Impostazioni",
                language: "Lingua",
                speechSpeed: "Velocità del parlato",
                noHistory: "Ancora nessuna descrizione.",
                clearHistory: "Cancella cronologia",
                cameraNeeded: "È necessario l'accesso alla fotocamera. Attivalo in Impostazioni.",
                notReady: "Impossibile preparare l'IA.",
                done: "Fatto",
                stop: "Ferma",
                clear: "Cancella",
                tapToHear: "Tocca per riascoltare")
        case .russian:
            return UIText(
                gettingReady: "Подготовка…",
                ready: "Готово. Коснитесь в любом месте, чтобы описать, что перед вами.",
                describing: "Смотрю…",
                tapToDescribe: "Коснитесь, чтобы описать",
                repeatLast: "Повторить",
                history: "История",
                settings: "Настройки",
                language: "Язык",
                speechSpeed: "Скорость речи",
                noHistory: "Пока нет описаний.",
                clearHistory: "Очистить историю",
                cameraNeeded: "Требуется доступ к камере. Включите его в Настройках.",
                notReady: "Не удалось подготовить ИИ.",
                done: "Готово",
                stop: "Стоп",
                clear: "Очистить",
                tapToHear: "Коснитесь, чтобы прослушать снова")
        }
    }
}
