param([string] $Theme = 'heartistica')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Convert-ToAbsorptionHeart {
    param(
        [Parameter(Mandatory)] [string] $Source,
        [Parameter(Mandatory)] [string] $Target,
        [switch] $KeepNeutralPixels
    )

    $sourceImage = $null
    $targetImage = $null

    try {
        $sourceImage = [System.Drawing.Bitmap]::new($Source)
        $targetImage = [System.Drawing.Bitmap]::new($sourceImage.Width, $sourceImage.Height,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($y = 0; $y -lt $sourceImage.Height; $y++) {
            for ($x = 0; $x -lt $sourceImage.Width; $x++) {
                $pixel = $sourceImage.GetPixel($x, $y)
                if ($pixel.A -eq 0) {
                    $targetImage.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
                    continue
                }

                $isColoredHeart = $pixel.R -gt ($pixel.G + 20) -and $pixel.R -gt ($pixel.B + 20)
                if ($KeepNeutralPixels -and -not $isColoredHeart) {
                    $targetImage.SetPixel($x, $y, $pixel)
                    continue
                }

                $intensity = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B)) / 255.0
                $red = [int](105 + 150 * $intensity)
                $green = [int](55 + 165 * $intensity)
                $blue = [int](5 + 55 * $intensity)
                $targetImage.SetPixel($x, $y,
                    [System.Drawing.Color]::FromArgb($pixel.A, $red, $green, $blue))
            }
        }
        $targetImage.Save($Target, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        if ($null -ne $sourceImage) { $sourceImage.Dispose() }
        if ($null -ne $targetImage) { $targetImage.Dispose() }
    }

    Write-Output "Generated $Target from $Source."
}

if ($Theme -notmatch '^[a-z0-9_-]+$' -or $Theme -eq 'resource_pack') {
    throw 'Invalid heart theme name.'
}
$textureDirectory = Join-Path $PSScriptRoot "..\common\src\main\resources\assets\heartistica\textures\gui\$Theme"
Convert-ToAbsorptionHeart `
    -Source (Join-Path $textureDirectory 'heart.png') `
    -Target (Join-Path $textureDirectory 'heart_absorption.png')
Convert-ToAbsorptionHeart `
    -Source (Join-Path $textureDirectory 'heart_half.png') `
    -Target (Join-Path $textureDirectory 'heart_absorption_half.png') `
    -KeepNeutralPixels
