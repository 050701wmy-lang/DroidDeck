# itch.io updates

An installed itch game is checked against the uploads itch lists for it. A game with an update shows an
Update label on its Library tile and an Update button on its game page. A Check for updates button sits
in the Library header while the itch chip is selected.

## Detection

The install record (`.droiddeck-store.json`) keeps three things from the install: the upload's
`updated_at` as `installVersion`, and `gameId`, `downloadKeyId` and `uploadId` in `extra`.
`ItchUpdates.evaluate` compares them with `GET /games/<id>/uploads`:

- the installed upload is still listed and its `updated_at` is later: an update to the same upload;
- the installed upload is gone: the newest Windows upload is offered as a replaced build;
- an install without an `uploadId` compares the newest Windows upload's stamp;
- an unknown installed version or no answer from itch never reports an update.

The check walks the installed itch games one at a time, one second apart, and logs
`itch.io: checked <n> installed games, <m> with updates`. It runs when the itch tab opens, at most every
six hours, and whenever the header button is pressed. A game itch did not answer for keeps its last
result, and the six-hour clock only advances after a fully answered pass. Results are kept in the itch
preferences until the update is installed or the account signs out.

## Installing

The Update button reruns the normal install over the game's own folder, keeping its storage location.
For a replaced build the game page asks first and names the new file. The installer marks the install
record as updating before it touches files, so a failure leaves a resumable record, and the finished
record carries the new `installVersion` and `uploadId`.
