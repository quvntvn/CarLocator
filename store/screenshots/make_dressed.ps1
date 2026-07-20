# Génère les captures "habillées" Play Store (1080x1920) à partir des captures brutes.
# Style : fond dégradé bleu (palette CarLocator), mockup téléphone arrondi + ombre, accroche + sous-titre.
# Sorties : dressed/ (FR) et dressed_en/ (EN). Relancer simplement le script pour tout régénérer.
Add-Type -AssemblyName System.Drawing

$rawDir = Join-Path $PSScriptRoot "raw"
$W = 1080; $H = 1920
$cx = [float]($W / 2)

# Écrans + couleurs partagés entre les langues (l'ordre narratif est identique).
$screens = @(
    @{ raw="01_home_parked.jpg";          top="#0E2A63"; bot="#2979FF" },
    @{ raw="08_pill_speed.jpg";           top="#123C8F"; bot="#2E86FF" },
    @{ raw="13_lock_parked_expanded.jpg"; top="#0C2456"; bot="#2470EE" },
    @{ raw="07_home_resume.jpg";          top="#0F2F73"; bot="#2979FF" },
    @{ raw="03_dialog_confirm.jpg";       top="#102C68"; bot="#2A74F0" },
    @{ raw="05_settings.jpg";             top="#0D2A66"; bot="#2C7DF5" }
)

$locales = @(
    @{
        outDir = Join-Path $PSScriptRoot "dressed"
        prefix = "store_habille"
        texts  = @(
            @{ hook="Ne cherchez plus votre voiture"; sub="Position enregistrée automatiquement" },
            @{ hook="Votre trajet, en un clin d'œil"; sub="Vitesse en direct dans la pastille" },
            @{ hook="Garé. Enregistré. Oublié.";      sub="Sauvegarde dès que le Bluetooth se coupe" },
            @{ hook="Reprenez le trajet d'un geste";  sub="Toujours connecté ? Un appui suffit" },
            @{ hook="Aucune fausse manœuvre";         sub="Confirmation avant chaque sauvegarde" },
            @{ hook="À votre façon";                  sub="km/h, mph, notifications au choix" }
        )
    },
    @{
        outDir = Join-Path $PSScriptRoot "dressed_en"
        prefix = "store_dressed"
        texts  = @(
            @{ hook="Never lose your car again";   sub="Your parking spot is saved automatically" },
            @{ hook="Your trip at a glance";       sub="Live speed in the status bar pill" },
            @{ hook="Parked. Saved. Forgotten.";   sub="Saves the moment Bluetooth disconnects" },
            @{ hook="Resume your trip in one tap"; sub="Still connected? Just tap" },
            @{ hook="No accidental overwrites";    sub="Confirmation before every save" },
            @{ hook="Make it yours";               sub="km/h, mph, notifications - your call" }
        )
    }
)

function New-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x,        $y,        $d, $d, 180, 90)
    $p.AddArc($x+$w-$d,  $y,        $d, $d, 270, 90)
    $p.AddArc($x+$w-$d,  $y+$h-$d,  $d, $d,   0, 90)
    $p.AddArc($x,        $y+$h-$d,  $d, $d,  90, 90)
    $p.CloseFigure()
    return $p
}

function Render-Slide($screen, $text, [string]$outFile) {
    $bmp = New-Object System.Drawing.Bitmap($W, $H)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    # fond dégradé vertical
    $cTop = [System.Drawing.ColorTranslator]::FromHtml($screen.top)
    $cBot = [System.Drawing.ColorTranslator]::FromHtml($screen.bot)
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0,0)), (New-Object System.Drawing.Point(0,$H)), $cTop, $cBot)
    $g.FillRectangle($bgBrush, 0, 0, $W, $H)

    # accroche (auto-ajustée pour tenir sur une ligne <= 950 px)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::Center
    $size = 68
    do {
        $font = New-Object System.Drawing.Font("Segoe UI", $size, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
        $meas = $g.MeasureString($text.hook, $font)
        if ($meas.Width -le 950) { break }
        $font.Dispose(); $size -= 2
    } while ($size -gt 42)
    $g.DrawString($text.hook, $font, [System.Drawing.Brushes]::White, [System.Drawing.PointF]::new($cx, 96), $fmt)
    $font.Dispose()

    # sous-titre
    $subFont = New-Object System.Drawing.Font("Segoe UI", 40, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(215, 255, 255, 255))
    $g.DrawString($text.sub, $subFont, $subBrush, [System.Drawing.PointF]::new($cx, 196), $fmt)
    $subFont.Dispose(); $subBrush.Dispose()

    # mockup téléphone
    $shotH  = 1470.0
    $shotW  = [math]::Round($shotH * 1200.0 / 2608.0)
    $bezel  = 16.0
    $mockW  = $shotW + 2*$bezel
    $mockH  = $shotH + 2*$bezel
    $mockX  = ($W - $mockW) / 2
    $mockY  = 320.0

    # ombre douce simulée
    foreach ($layer in @(@(28,22), @(20,15), @(12,9), @(6,5))) {
        $spread = $layer[0]; $alpha = $layer[1]
        $shPath = New-RoundedPath ($mockX-$spread) ($mockY-$spread+14) ($mockW+2*$spread) ($mockH+2*$spread) (58+$spread)
        $shBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($alpha, 0, 0, 20))
        $g.FillPath($shBrush, $shPath)
        $shBrush.Dispose(); $shPath.Dispose()
    }

    # cadre (bezel)
    $framePath = New-RoundedPath $mockX $mockY $mockW $mockH 58
    $frameBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml("#0A0A0F"))
    $g.FillPath($frameBrush, $framePath)
    $frameBrush.Dispose(); $framePath.Dispose()

    # capture clippée en coins arrondis
    $img = [System.Drawing.Image]::FromFile((Join-Path $rawDir $screen.raw))
    $clipPath = New-RoundedPath ($mockX+$bezel) ($mockY+$bezel) $shotW $shotH 44
    $g.SetClip($clipPath)
    $g.DrawImage($img, [System.Drawing.RectangleF]::new($mockX+$bezel, $mockY+$bezel, $shotW, $shotH))
    $g.ResetClip()
    $clipPath.Dispose(); $img.Dispose()

    # liseré discret
    $borderPath = New-RoundedPath $mockX $mockY $mockW $mockH 58
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(60, 255, 255, 255), 2)
    $g.DrawPath($pen, $borderPath)
    $pen.Dispose(); $borderPath.Dispose()

    $bmp.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose(); $bgBrush.Dispose()
    Write-Output "OK  $outFile"
}

foreach ($loc in $locales) {
    New-Item -ItemType Directory -Force $loc.outDir | Out-Null
    for ($i = 0; $i -lt $screens.Count; $i++) {
        $out = Join-Path $loc.outDir ("{0}_{1:D2}.png" -f $loc.prefix, ($i+1))
        Render-Slide $screens[$i] $loc.texts[$i] $out
    }
}
