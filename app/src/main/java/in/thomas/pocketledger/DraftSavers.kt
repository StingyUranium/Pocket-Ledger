package `in`.thomas.pocketledger

import androidx.compose.runtime.saveable.Saver

val EntryDraftSaver = Saver<Entry?, ArrayList<String>>(
    save = { e -> if (e == null) arrayListOf() else arrayListOf(
        e.id, e.accountId, e.destinationId.orEmpty(), e.type, e.amount.toString(), e.date.toString(),
        e.dateOnly.toString(), e.payee, e.category, e.note, e.smsKey.orEmpty(), e.deleted.toString()
    ) },
    restore = { e -> if (e.isEmpty()) null else Entry(
        id = e[0], accountId = e[1], destinationId = e[2].ifEmpty { null }, type = e[3],
        amount = e[4].toLong(), date = e[5].toLong(), dateOnly = e[6].toBoolean(), payee = e[7],
        category = e[8], note = e[9], smsKey = e[10].ifEmpty { null }, deleted = e[11].toBoolean()
    ) }
)
val AccountDraftSaver = Saver<Account?, ArrayList<String>>(
    save = { a -> if (a == null) arrayListOf() else arrayListOf(a.id, a.name, a.bank, a.suffix, a.opening.toString(), a.openingDate.toString()) },
    restore = { a -> if (a.isEmpty()) null else Account(a[0], a[1], a[2], a[3], a[4].toLong(), a[5].toLong()) }
)
