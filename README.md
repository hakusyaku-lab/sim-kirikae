# SIM切替（SimKirikae）

Androidのデータ通信SIMを①/②で1タップ切り替えるアプリ。Shizuku が必要。

- アプリ画面：大きな「SIM①」「SIM②」ボタン
- ホーム画面ショートカット：アイコン長押し →「SIM①」「SIM②」「SIM切替」
- クイック設定タイル：「データ SIM①」「データ SIM②」

切り替えの処理は [DataSimTile](https://github.com/Mygod/DataSimTile)（Apache-2.0）を参考にしています。
APKは GitHub Actions でビルドされ、Releases の `latest` に置かれます。
