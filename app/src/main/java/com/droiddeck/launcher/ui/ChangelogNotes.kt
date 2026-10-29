package com.droiddeck.launcher.ui

import com.droiddeck.launcher.R
import com.droiddeck.launcher.update.AppUpdates

internal val changelogNotes = mapOf(
    "eaaecf066143a6fd5efda6d53344f368c4cb48a3" to Triple(R.string.changelog_title_eaaecf066143a6fd5efda6d53344f368c4cb48a3, R.string.changelog_summary_eaaecf066143a6fd5efda6d53344f368c4cb48a3, 1793031538000L),
    "9b68b33e73228c08cee2802d51cb4fa55027ad34" to Triple(R.string.changelog_title_9b68b33e73228c08cee2802d51cb4fa55027ad34, R.string.changelog_summary_9b68b33e73228c08cee2802d51cb4fa55027ad34, 1793031307000L),
    "d65912f0c52a642b2c76be28145a700cf5d183eb" to Triple(R.string.changelog_title_d65912f0c52a642b2c76be28145a700cf5d183eb, 0, 1793030319000L),
    "5d945d7784248be6cd294c3ce830c56ef513bbd8" to Triple(R.string.changelog_title_5d945d7784248be6cd294c3ce830c56ef513bbd8, R.string.changelog_summary_5d945d7784248be6cd294c3ce830c56ef513bbd8, 1793029422000L),
    "ee1b3b26e1f7b19216b17a1d49cb070d77980f65" to Triple(R.string.changelog_title_ee1b3b26e1f7b19216b17a1d49cb070d77980f65, R.string.changelog_summary_ee1b3b26e1f7b19216b17a1d49cb070d77980f65, 1793027368000L),
    "e492a78f55c60b717a1ec9858ae869871925564e" to Triple(R.string.changelog_title_e492a78f55c60b717a1ec9858ae869871925564e, R.string.changelog_summary_e492a78f55c60b717a1ec9858ae869871925564e, 1793026225000L),
    "fd9de4a21e7c5b49e36ecb05e475477ce85c8d7b" to Triple(R.string.changelog_title_fd9de4a21e7c5b49e36ecb05e475477ce85c8d7b, R.string.changelog_summary_fd9de4a21e7c5b49e36ecb05e475477ce85c8d7b, 1793024789000L),
    "1b3814a5876788962141d1a53c4ffcd8cc367c9c" to Triple(R.string.changelog_title_1b3814a5876788962141d1a53c4ffcd8cc367c9c, R.string.changelog_summary_1b3814a5876788962141d1a53c4ffcd8cc367c9c, 1793021593000L),
    "78f95c5a0c1a4153aa0426fbc95ed0add0139471" to Triple(R.string.changelog_title_78f95c5a0c1a4153aa0426fbc95ed0add0139471, R.string.changelog_summary_78f95c5a0c1a4153aa0426fbc95ed0add0139471, 1793021501000L),
    "db56cff880e84c777740d377049c8e0e3a844acf" to Triple(R.string.changelog_title_db56cff880e84c777740d377049c8e0e3a844acf, R.string.changelog_summary_db56cff880e84c777740d377049c8e0e3a844acf, 1793020479000L),
    "3fdc74051c6b1e0b1ec8a92926fd82144f981a37" to Triple(R.string.changelog_title_3fdc74051c6b1e0b1ec8a92926fd82144f981a37, R.string.changelog_summary_3fdc74051c6b1e0b1ec8a92926fd82144f981a37, 1792938132000L),
    "2a695051991e537dec115ecbb664240bf69daa10" to Triple(R.string.changelog_title_2a695051991e537dec115ecbb664240bf69daa10, R.string.changelog_summary_2a695051991e537dec115ecbb664240bf69daa10, 1792932770000L),
    "1f42f512e2594e741abbefb621f5fa38ec2bc709" to Triple(R.string.changelog_title_1f42f512e2594e741abbefb621f5fa38ec2bc709, R.string.changelog_summary_1f42f512e2594e741abbefb621f5fa38ec2bc709, 1792902154000L),
    "c5d7cdc058981094a1b48603d5f25b48ff0bcda6" to Triple(R.string.changelog_title_c5d7cdc058981094a1b48603d5f25b48ff0bcda6, R.string.changelog_summary_c5d7cdc058981094a1b48603d5f25b48ff0bcda6, 1792899296000L),
    "5ca1f610abfcc2327034ef5f5a04a98a3ac3730b" to Triple(R.string.changelog_title_5ca1f610abfcc2327034ef5f5a04a98a3ac3730b, R.string.changelog_summary_5ca1f610abfcc2327034ef5f5a04a98a3ac3730b, 1792893493000L),
    "82d8eb3b79df447c8df577584eb58bf8965d769a" to Triple(R.string.changelog_title_82d8eb3b79df447c8df577584eb58bf8965d769a, R.string.changelog_summary_82d8eb3b79df447c8df577584eb58bf8965d769a, 1792891745000L),
    "8d407aa1e80efc041ec86c181f603838389aea42" to Triple(R.string.changelog_title_8d407aa1e80efc041ec86c181f603838389aea42, R.string.changelog_summary_8d407aa1e80efc041ec86c181f603838389aea42, 1792887533000L),
    "aca7fca19fe087dffe89a86a1a92eebd190cdf20" to Triple(R.string.changelog_title_aca7fca19fe087dffe89a86a1a92eebd190cdf20, R.string.changelog_summary_aca7fca19fe087dffe89a86a1a92eebd190cdf20, 1792886759000L),
    "74a6a88df8c3371dd4570e1ce9e258832b380e11" to Triple(R.string.changelog_title_74a6a88df8c3371dd4570e1ce9e258832b380e11, R.string.changelog_summary_74a6a88df8c3371dd4570e1ce9e258832b380e11, 1792883679000L),
    "dc08385411228bb1378f1eeadef317acf7672b9d" to Triple(R.string.changelog_title_dc08385411228bb1378f1eeadef317acf7672b9d, R.string.changelog_summary_dc08385411228bb1378f1eeadef317acf7672b9d, 1792879995000L),
    "43a0ce93f9fec1ea14e7017fe0d95a7d65ba33a6" to Triple(R.string.changelog_title_43a0ce93f9fec1ea14e7017fe0d95a7d65ba33a6, R.string.changelog_summary_43a0ce93f9fec1ea14e7017fe0d95a7d65ba33a6, 1793092918000L),
    "07a0a7e34433882f6fba2cd722783e63e03b4f40" to Triple(R.string.changelog_title_07a0a7e34433882f6fba2cd722783e63e03b4f40, R.string.changelog_summary_07a0a7e34433882f6fba2cd722783e63e03b4f40, 1793075750000L),
    "c25d54acedcd0db899ce569751a9adfc76156e6a" to Triple(R.string.changelog_title_c25d54acedcd0db899ce569751a9adfc76156e6a, R.string.changelog_summary_c25d54acedcd0db899ce569751a9adfc76156e6a, 1793074872000L),
    "4c7e59ed8b58c570e6b8220c356dad39e9a34ad3" to Triple(R.string.changelog_title_4c7e59ed8b58c570e6b8220c356dad39e9a34ad3, R.string.changelog_summary_4c7e59ed8b58c570e6b8220c356dad39e9a34ad3, 1793072126000L),
    "0e9a31ccfe4fcce2828d067c7fa0e6db79badea5" to Triple(R.string.changelog_title_0e9a31ccfe4fcce2828d067c7fa0e6db79badea5, R.string.changelog_summary_0e9a31ccfe4fcce2828d067c7fa0e6db79badea5, 1793070830000L),
    "8749154cbf3974d6f9de7966ce2a106657b49ad7" to Triple(R.string.changelog_title_8749154cbf3974d6f9de7966ce2a106657b49ad7, R.string.changelog_summary_8749154cbf3974d6f9de7966ce2a106657b49ad7, 1793064470000L),
    "bb9a64fa5fc08ddfa0f5fad6e8a38bf78e88d757" to Triple(R.string.changelog_title_bb9a64fa5fc08ddfa0f5fad6e8a38bf78e88d757, R.string.changelog_summary_bb9a64fa5fc08ddfa0f5fad6e8a38bf78e88d757, 1793062350000L),
    "9555cb9f650dabd79a542cebfe6239831648937f" to Triple(R.string.changelog_title_9555cb9f650dabd79a542cebfe6239831648937f, R.string.changelog_summary_9555cb9f650dabd79a542cebfe6239831648937f, 1793062269000L),
    "70d8fc6559c41910c8d48056987e7c52516259a5" to Triple(R.string.changelog_title_70d8fc6559c41910c8d48056987e7c52516259a5, R.string.changelog_summary_70d8fc6559c41910c8d48056987e7c52516259a5, 1793054792000L),
    "d905f03bf725090e62340492b192591cbb035652" to Triple(R.string.changelog_title_d905f03bf725090e62340492b192591cbb035652, R.string.changelog_summary_d905f03bf725090e62340492b192591cbb035652, 1793053861000L),
    "8c4f97a1705d34f042d79a8f1d31c401f0872d4e" to Triple(R.string.changelog_title_8c4f97a1705d34f042d79a8f1d31c401f0872d4e, R.string.changelog_summary_8c4f97a1705d34f042d79a8f1d31c401f0872d4e, 1793180257000L),
    "8549ac9ce8128a036075e9402419de6eec103dfd" to Triple(R.string.changelog_title_8549ac9ce8128a036075e9402419de6eec103dfd, R.string.changelog_summary_8549ac9ce8128a036075e9402419de6eec103dfd, 1793180224000L),
    "213c2da57e67e6062afcbd4db531c9c909059da8" to Triple(R.string.changelog_title_213c2da57e67e6062afcbd4db531c9c909059da8, R.string.changelog_summary_213c2da57e67e6062afcbd4db531c9c909059da8, 1793180106000L),
    "f4656ad83aeb9764e6c75beffb062bd411ed24d7" to Triple(R.string.changelog_title_f4656ad83aeb9764e6c75beffb062bd411ed24d7, R.string.changelog_summary_f4656ad83aeb9764e6c75beffb062bd411ed24d7, 1793160998000L),
    "8164f9fae137d4d7d41193f124535b41fb9bf0c8" to Triple(R.string.changelog_title_8164f9fae137d4d7d41193f124535b41fb9bf0c8, R.string.changelog_summary_8164f9fae137d4d7d41193f124535b41fb9bf0c8, 1793155737000L),
    "374d4783fc76828b2456ba15003d236c769808ec" to Triple(R.string.changelog_title_374d4783fc76828b2456ba15003d236c769808ec, R.string.changelog_summary_374d4783fc76828b2456ba15003d236c769808ec, 1793155492000L),
    "3716c8acdf80011a700ceffbd6e59b0a7d234ac4" to Triple(R.string.changelog_title_3716c8acdf80011a700ceffbd6e59b0a7d234ac4, R.string.changelog_summary_3716c8acdf80011a700ceffbd6e59b0a7d234ac4, 1793155381000L),
    "eceda6e84087756170652637e694f407fee2ba34" to Triple(R.string.changelog_title_eceda6e84087756170652637e694f407fee2ba34, R.string.changelog_summary_eceda6e84087756170652637e694f407fee2ba34, 1793155052000L),
    "82c07c4be79a52a6a310b570b8993a462ac83ef8" to Triple(R.string.changelog_title_82c07c4be79a52a6a310b570b8993a462ac83ef8, R.string.changelog_summary_82c07c4be79a52a6a310b570b8993a462ac83ef8, 1793152622000L),
    "4ef6b53eb09f9cd5150f288be49c5c740e400e48" to Triple(R.string.changelog_title_4ef6b53eb09f9cd5150f288be49c5c740e400e48, R.string.changelog_summary_4ef6b53eb09f9cd5150f288be49c5c740e400e48, 1793143444000L),
    "27bdc68867309221ede2e2ab44fe0ada9d1631f5" to Triple(R.string.changelog_title_27bdc68867309221ede2e2ab44fe0ada9d1631f5, R.string.changelog_summary_27bdc68867309221ede2e2ab44fe0ada9d1631f5, 1793202678000L),
    "6ecf1ede2cae76d8c2259f1a7cc96a9307a157d1" to Triple(R.string.changelog_title_6ecf1ede2cae76d8c2259f1a7cc96a9307a157d1, R.string.changelog_summary_6ecf1ede2cae76d8c2259f1a7cc96a9307a157d1, 1793200809000L),
    "9294a60172be652defd92fae5fcf22f38544dc80" to Triple(R.string.changelog_title_9294a60172be652defd92fae5fcf22f38544dc80, R.string.changelog_summary_9294a60172be652defd92fae5fcf22f38544dc80, 1793200068000L),
    "4aa19b6a39be5ee5d861680ab716b478984f3992" to Triple(R.string.changelog_title_4aa19b6a39be5ee5d861680ab716b478984f3992, R.string.changelog_summary_4aa19b6a39be5ee5d861680ab716b478984f3992, 1793197415000L),
    "8f6b2a412308f875c93c585bb3289442b054012a" to Triple(R.string.changelog_title_8f6b2a412308f875c93c585bb3289442b054012a, R.string.changelog_summary_8f6b2a412308f875c93c585bb3289442b054012a, 1793195533000L),
    "9d82df6981bd7b82ee24e976c0ffa22c8ee50d50" to Triple(R.string.changelog_title_9d82df6981bd7b82ee24e976c0ffa22c8ee50d50, R.string.changelog_summary_9d82df6981bd7b82ee24e976c0ffa22c8ee50d50, 1793195327000L),
    "d1bd23df161de27520e05601f0ad71ab45f78a2c" to Triple(R.string.changelog_title_d1bd23df161de27520e05601f0ad71ab45f78a2c, R.string.changelog_summary_d1bd23df161de27520e05601f0ad71ab45f78a2c, 1793248387000L),
    "2410b8c67c459419e89f29ae1af88f7a8368628d" to Triple(R.string.changelog_title_2410b8c67c459419e89f29ae1af88f7a8368628d, R.string.changelog_summary_2410b8c67c459419e89f29ae1af88f7a8368628d, 1793246290000L),
    "64950099a3c05b876ba981e838eda6d8fcf811ec" to Triple(R.string.changelog_title_64950099a3c05b876ba981e838eda6d8fcf811ec, R.string.changelog_summary_64950099a3c05b876ba981e838eda6d8fcf811ec, 1793244101000L),
    "0516d023fa03362e5a34b5436fbda5f6008d4d4f" to Triple(R.string.changelog_title_0516d023fa03362e5a34b5436fbda5f6008d4d4f, R.string.changelog_summary_0516d023fa03362e5a34b5436fbda5f6008d4d4f, 1793242623000L),
    "110cfc28f81291d7bde494e3271571ff7192e958" to Triple(R.string.changelog_title_110cfc28f81291d7bde494e3271571ff7192e958, R.string.changelog_summary_110cfc28f81291d7bde494e3271571ff7192e958, 1793239788000L),
    "e871557bf2492f05ed2b17a31d560a5ae62ca13d" to Triple(R.string.changelog_title_e871557bf2492f05ed2b17a31d560a5ae62ca13d, R.string.changelog_summary_e871557bf2492f05ed2b17a31d560a5ae62ca13d, 1793233635000L),
    "87572a7f9138328d3deaf12058e0e02f58c7fdf5" to Triple(R.string.changelog_title_87572a7f9138328d3deaf12058e0e02f58c7fdf5, R.string.changelog_summary_87572a7f9138328d3deaf12058e0e02f58c7fdf5, 1793231373000L),
    "1e92d10fa53b98086b4fdfd6629990d71be714b4" to Triple(R.string.changelog_title_1e92d10fa53b98086b4fdfd6629990d71be714b4, R.string.changelog_summary_1e92d10fa53b98086b4fdfd6629990d71be714b4, 1793226637000L),
    "92787dd0f015455481d09398ac2ee0f42c7e6883" to Triple(R.string.changelog_title_92787dd0f015455481d09398ac2ee0f42c7e6883, R.string.changelog_summary_92787dd0f015455481d09398ac2ee0f42c7e6883, 1793224189000L),
    "aef1f85aa9d579e24a1e3bd7c8dae2bd1a3fc84b" to Triple(R.string.changelog_title_aef1f85aa9d579e24a1e3bd7c8dae2bd1a3fc84b, R.string.changelog_summary_aef1f85aa9d579e24a1e3bd7c8dae2bd1a3fc84b, 1793221483000L),
)

internal fun changelogChanges(catalog: AppUpdates.Catalog?): List<AppUpdates.PreviewChange> =
    buildList {
        catalog?.preview?.let {
            add(AppUpdates.PreviewChange(it.commit, it.title.ifBlank { it.tag }, it.summary, it.publishedAt))
        }
        addAll(catalog?.recentPreviews.orEmpty())
        addAll(changelogNotes.map { (commit, note) -> AppUpdates.PreviewChange(commit, "", "", note.third) })
    }.distinctBy { it.commit.lowercase() }.sortedByDescending { it.publishedAt }
