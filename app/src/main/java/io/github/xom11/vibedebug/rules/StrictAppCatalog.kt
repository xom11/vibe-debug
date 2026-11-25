package io.github.xom11.vibedebug.rules

/** High-level domain category for apps known to restrict debugging or Developer Options. */
enum class StrictAppCategory {
    GOV_ID,
    BANKING,
    EWALLET,
    SECURITIES_FINTECH,
    ENTERPRISE_AUTH,
}

/**
 * Curated on-device catalog of verified Google Play package IDs across Vietnam public services,
 * banking, e-wallets, fintech/securities, and international strict banking/ID/enterprise apps.
 */
object StrictAppCatalog {

    val ENTRIES: Map<String, StrictAppCategory> = mapOf(
        // Government, Digital ID & Public Services (Vietnam & Regional)
        "com.vnid" to StrictAppCategory.GOV_ID, // VNeID
        "com.bhxhapp" to StrictAppCategory.GOV_ID, // VssID - Bảo hiểm xã hội Việt Nam
        "com.etax.icanhan" to StrictAppCategory.GOV_ID, // eTax Mobile
        "com.ots.c08.vnetraffic" to StrictAppCategory.GOV_ID, // VNeTraffic
        "com.viettel.citizens.hanoi" to StrictAppCategory.GOV_ID, // iHanoi
        "vn.stttt.hues" to StrictAppCategory.GOV_ID, // Hue-S
        "vn.com.vetc.app" to StrictAppCategory.GOV_ID, // VETC
        "com.viettel.etc.epass" to StrictAppCategory.GOV_ID, // ePass
        "sg.ndi.sp" to StrictAppCategory.GOV_ID, // Singpass

        // Banking (Vietnam)
        "com.VCB" to StrictAppCategory.BANKING,
        "com.vnpay.bidv" to StrictAppCategory.BANKING,
        "com.vietinbank.ipay" to StrictAppCategory.BANKING,
        "com.vnpay.Agribank3g" to StrictAppCategory.BANKING,
        "vn.com.techcombank.bb.app" to StrictAppCategory.BANKING,
        "com.mbmobile" to StrictAppCategory.BANKING,
        "com.vnpay.vpbankonline" to StrictAppCategory.BANKING,
        "mobile.acb.com.vn" to StrictAppCategory.BANKING,
        "com.acb.acbb.prod" to StrictAppCategory.BANKING,
        "com.tpb.mb.gprsandroid" to StrictAppCategory.BANKING,
        "com.vib.myvib2" to StrictAppCategory.BANKING,
        "com.sacombank.ewallet" to StrictAppCategory.BANKING,
        "src.com.sacombank" to StrictAppCategory.BANKING,
        "com.vnpay.hdbank" to StrictAppCategory.BANKING,
        "vn.shb.saha.mbanking" to StrictAppCategory.BANKING,
        "vn.com.ocb.awe" to StrictAppCategory.BANKING,
        "com.ocb.liobank" to StrictAppCategory.BANKING,
        "vn.com.msb.smartBanking" to StrictAppCategory.BANKING,
        "vn.com.seabank.mb1" to StrictAppCategory.BANKING,
        "vn.com.lpb.lienviet24h" to StrictAppCategory.BANKING,
        "com.vnpay.EximBankOmni" to StrictAppCategory.BANKING,
        "ops.namabank.com.vn" to StrictAppCategory.BANKING,
        "vn.abbank.retail" to StrictAppCategory.BANKING,
        "com.pvcombank.retail" to StrictAppCategory.BANKING,
        "com.sunshine.ksbank" to StrictAppCategory.BANKING,
        "com.vnpay.bvbank" to StrictAppCategory.BANKING,
        "com.vn.vietbankomni" to StrictAppCategory.BANKING,
        "com.ncb.bank" to StrictAppCategory.BANKING,
        "com.pgbank.mobile" to StrictAppCategory.BANKING,
        "com.bab.retailUAT" to StrictAppCategory.BANKING,
        "com.vnpay.SCB" to StrictAppCategory.BANKING,
        "com.vnpay.coopbank" to StrictAppCategory.BANKING,
        "com.vnpay.sgbank" to StrictAppCategory.BANKING,
        "com.vnpay.ivb" to StrictAppCategory.BANKING,
        "com.vnpay.publicbank" to StrictAppCategory.BANKING,
        "com.mbv.mobilebanking" to StrictAppCategory.BANKING,
        "xyz.be.cake" to StrictAppCategory.BANKING,
        "io.lifestyle.plus" to StrictAppCategory.BANKING,
        "vn.tnex.consumer" to StrictAppCategory.BANKING,
        "com.shinhan.global.vn.bank" to StrictAppCategory.BANKING,
        "vn.com.woori.smart" to StrictAppCategory.BANKING,
        "vn.cimbbank.octo" to StrictAppCategory.BANKING,
        "com.uob.mightyvn" to StrictAppCategory.BANKING,
        "com.kasikornbank.kplus.vn" to StrictAppCategory.BANKING,

        // Banking (Global & Regional)
        "com.dbs.sg.dbsmbanking" to StrictAppCategory.BANKING,
        "com.ocbc.mobile" to StrictAppCategory.BANKING,
        "com.uob.mighty.app" to StrictAppCategory.BANKING,
        "com.maybank2u.life" to StrictAppCategory.BANKING,
        "com.bca" to StrictAppCategory.BANKING,
        "id.bmri.livin" to StrictAppCategory.BANKING,
        "id.co.bri.brimo" to StrictAppCategory.BANKING,
        "com.kasikorn.retail.mbanking.wap" to StrictAppCategory.BANKING,
        "com.scb.phone" to StrictAppCategory.BANKING,
        "ktbcs.netbank" to StrictAppCategory.BANKING,
        "com.bbl.mobilebanking" to StrictAppCategory.BANKING,
        "com.kakaobank.channel" to StrictAppCategory.BANKING,
        "com.kbstar.kbbank" to StrictAppCategory.BANKING,
        "com.shinhan.sbanking" to StrictAppCategory.BANKING,
        "com.sbi.lotusintouch" to StrictAppCategory.BANKING,
        "com.csam.icici.bank.imobile" to StrictAppCategory.BANKING,
        "com.axis.mobile" to StrictAppCategory.BANKING,
        "com.revolut.revolut" to StrictAppCategory.BANKING,
        "com.transferwise.android" to StrictAppCategory.BANKING,
        "co.uk.getmondo" to StrictAppCategory.BANKING,
        "com.starlingbank.android" to StrictAppCategory.BANKING,
        "de.number26.android" to StrictAppCategory.BANKING,
        "uk.co.hsbc.hsbcukmobilebanking" to StrictAppCategory.BANKING,
        "com.barclays.android.barclaysmobilebanking" to StrictAppCategory.BANKING,
        "com.chase.sig.android" to StrictAppCategory.BANKING,
        "com.infonow.bofa" to StrictAppCategory.BANKING,
        "com.wf.wellsfargomobile" to StrictAppCategory.BANKING,
        "com.citi.citimobile" to StrictAppCategory.BANKING,
        "com.konylabs.capitalone" to StrictAppCategory.BANKING,

        // E-Wallets & Payments (Vietnam)
        "com.mservice.momotransfer" to StrictAppCategory.EWALLET,
        "vn.com.vng.zalopay" to StrictAppCategory.EWALLET,
        "com.bplus.vtpay" to StrictAppCategory.EWALLET,
        "vnpay.smartacccount" to StrictAppCategory.EWALLET,
        "vnptpay.vnptmedia.vnpt.com.vnptpay" to StrictAppCategory.EWALLET,
        "com.beeasy.toppay" to StrictAppCategory.EWALLET,
        "com.viettel.appviettel" to StrictAppCategory.EWALLET,
        "vms.com.vn.mymobifone" to StrictAppCategory.EWALLET,

        // E-Wallets & Payments (Global & Regional)
        "my.com.tngdigital.ewallet" to StrictAppCategory.EWALLET,
        "com.globe.gcash.android" to StrictAppCategory.EWALLET,
        "com.paymaya" to StrictAppCategory.EWALLET,
        "com.gojek.gopay" to StrictAppCategory.EWALLET,
        "id.dana" to StrictAppCategory.EWALLET,
        "ovo.id" to StrictAppCategory.EWALLET,
        "th.co.truemoney.wallet" to StrictAppCategory.EWALLET,
        "jp.ne.paypay.android.app" to StrictAppCategory.EWALLET,
        "viva.republica.toss" to StrictAppCategory.EWALLET,
        "in.org.npci.upiapp" to StrictAppCategory.EWALLET,
        "com.phonepe.app" to StrictAppCategory.EWALLET,
        "net.one97.paytm" to StrictAppCategory.EWALLET,
        "com.google.android.apps.nbu.paisa.user" to StrictAppCategory.EWALLET,
        "com.paypal.android.p2pmobile" to StrictAppCategory.EWALLET,
        "com.venmo" to StrictAppCategory.EWALLET,
        "com.squareup.cash" to StrictAppCategory.EWALLET,
        "com.google.android.apps.walletnfcrel" to StrictAppCategory.EWALLET,
        "com.samsung.android.spay" to StrictAppCategory.EWALLET,

        // Securities, Consumer Finance & Crypto
        "com.fecredit.feonline" to StrictAppCategory.SECURITIES_FINTECH,
        "com.finaccel.android" to StrictAppCategory.SECURITIES_FINTECH,
        "vn.com.vpbs.smartone" to StrictAppCategory.SECURITIES_FINTECH,
        "vn.com.vndirect.stocks" to StrictAppCategory.SECURITIES_FINTECH,
        "com.fincorp.fmarket" to StrictAppCategory.SECURITIES_FINTECH,
        "vn.finhay.finhay" to StrictAppCategory.SECURITIES_FINTECH,
        "com.binance.dev" to StrictAppCategory.SECURITIES_FINTECH,
        "com.coinbase.android" to StrictAppCategory.SECURITIES_FINTECH,
        "com.okinc.okex.gp" to StrictAppCategory.SECURITIES_FINTECH,
        "com.bybit.app" to StrictAppCategory.SECURITIES_FINTECH,

        // Enterprise & Multi-Factor Auth
        "com.microsoft.windowsintune.companyportal" to StrictAppCategory.ENTERPRISE_AUTH,
        "com.okta.android.auth" to StrictAppCategory.ENTERPRISE_AUTH,
        "com.duosecurity.duomobile" to StrictAppCategory.ENTERPRISE_AUTH,
    )

    val KNOWN_STRICT_PACKAGES: Set<String> = ENTRIES.keys

    /** Returns the curated [StrictAppCategory] for [packageName], or null if unlisted. */
    fun categoryFor(packageName: String): StrictAppCategory? = ENTRIES[packageName]
}
