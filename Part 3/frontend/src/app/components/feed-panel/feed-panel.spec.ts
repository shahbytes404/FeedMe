import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FeedPanel } from './feed-panel';

describe('FeedPanel', () => {
  let component: FeedPanel;
  let fixture: ComponentFixture<FeedPanel>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedPanel],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedPanel);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
