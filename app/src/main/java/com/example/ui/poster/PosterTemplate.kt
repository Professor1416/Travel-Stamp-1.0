package com.example.ui.poster

/**
 * Visual template options for 9:16 story/poster export.
 */
enum class PosterTemplate(val title: String, val description: String) {
    /**
     * Template A: Photo + Stamp
     * User journey photo is the hero visual with official stamp overlay.
     */
    PHOTO_STAMP("Photo + Stamp", "Expedition photo hero with overlaid official stamp"),

    /**
     * Template B: Passport / Stamp Focused
     * Official stamp is the hero on an authentic passport parchment background.
     */
    PASSPORT_STAMP("Passport Focus", "Clean passport parchment with hero travel stamp"),

    /**
     * Template C: Story Slip
     * User journey photo with official stamp on a polished parchment ticket layout.
     */
    STORY_SLIP("Story Slip", "User photo and official stamp on polished parchment ticket layout")
}
