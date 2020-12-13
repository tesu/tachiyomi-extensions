package eu.kanade.tachiyomi.extension.all.genkan

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GenkanFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LeviatanScans(),
        LeviatanScansES(),
        HunlightScans(),
        ZeroScans(),
        ReaperScans(),
        TheNonamesScans(),
        HatigarmScans(),
        EdelgardeScans(),
        SecretScans(),
        MethodScans(),
        SKScans(),
    )
}

/* Genkan class is for the latest version of Genkan CMS
   GenkanOriginal is for the initial version of the CMS that didn't have its own search function  */

class LeviatanScans : Genkan("Leviatan Scans", "https://leviatanscans.com", "en")
class LeviatanScansES : GenkanOriginal("Leviatan Scans", "https://es.leviatanscans.com", "es")
class HunlightScans : Genkan("Hunlight Scans", "https://hunlight-scans.info", "en")
class ZeroScans : Genkan("ZeroScans", "https://zeroscans.com", "en")
// Search isn't working on Reaper's website, use GenkanOriginal for now
class ReaperScans : GenkanOriginal("Reaper Scans", "https://reaperscans.com", "en")
class TheNonamesScans : Genkan("The Nonames Scans", "https://the-nonames.com", "en")
class HatigarmScans : GenkanOriginal("Hatigarm Scans", "https://hatigarmscanz.net", "en") {
    override val versionId = 2
}
class EdelgardeScans : Genkan("Edelgarde Scans", "https://edelgardescans.com", "en")
class SecretScans : GenkanOriginal("SecretScans", "https://secretscans.co", "en")
class MethodScans : Genkan("Method Scans", "https://methodscans.com", "en")
class SKScans : Genkan("Sleeping Knight Scans", "https://skscans.com", "en")
