package com.cilia.wallet.external.partner.model

import java.io.Serializable


data class MainMenuContent(val pages: List<MainMenuPage>)

data class MainMenuPage(val tabName: String,
                        val tabIndex: Int,
                        val imageUrl: String,
                        val link: String) : CommonContent(), Serializable