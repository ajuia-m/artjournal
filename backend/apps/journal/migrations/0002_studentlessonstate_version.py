from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("journal", "0001_initial"),
    ]

    operations = [
        migrations.AddField(
            model_name="studentlessonstate",
            name="version",
            field=models.PositiveBigIntegerField(default=1),
        ),
    ]
